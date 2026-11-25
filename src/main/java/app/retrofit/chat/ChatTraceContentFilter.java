package app.retrofit.chat;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

/// Adds what Spring AI leaves out to the spans of the chat's questions (chat ADR 0007): the prompt's name and version
/// (chat ADR 0008), the conversation as `session.id`, the caller's id as `user.id`, the texts as [TraceText]
/// attributes, which [MaskingSpanExporter] masks before they leave the app, and the OpenInference span kind, without
/// which Phoenix lists a span's kind as unknown.
///
/// While the stored `appLlmSpansExported` is off, a model call's span is a `CHAIN` without its token counts and model
/// names, so only LiteLLM's span of the call counts its tokens and cost (chat ADR 0016). The filter must run after
/// [StreamedCacheUsage], which adds the cache counts this one removes.
///
/// Every value is a high-cardinality key, so it goes on the span and never becomes a metric tag. The filter runs when an
/// observation stops, on the thread that stops it, so it only copies what the observation already holds.
final class ChatTraceContentFilter implements ObservationFilter {

	private static final String SPAN_KIND = "openinference.span.kind";

	/// What Phoenix counts and prices a span by: it reads the token counts and the model name from these.
	private static final List<String> COUNTED = List.of("gen_ai.usage.input_tokens", "gen_ai.usage.output_tokens",
			"gen_ai.usage.total_tokens", "gen_ai.usage.cache_read.input_tokens",
			"gen_ai.usage.cache_creation.input_tokens", "gen_ai.request.model", "gen_ai.response.model");

	private final SystemPrompt systemPrompt;

	private final JsonMapper json;

	ChatTraceContentFilter(SystemPrompt systemPrompt, JsonMapper json) {
		this.systemPrompt = systemPrompt;
		this.json = json;
	}

	@Override
	public Observation.Context map(Observation.Context context) {
		// Only the chat's requests are observed (onlyChatRequests), so every request span is the root of a question.
		if (context instanceof ServerRequestObservationContext) {
			add(context, SPAN_KIND, "CHAIN");
			return context;
		}
		var question = question(context);
		if (question == null) {
			return context;
		}
		switch (context) {
			case ChatClientObservationContext client -> add(client, SPAN_KIND, "CHAIN");
			case ChatModelObservationContext model -> {
				if (!question.llmSpanExported()) {
					add(model, SPAN_KIND, "CHAIN");
					COUNTED.forEach(key -> model.removeLowCardinalityKeyValue(key).removeHighCardinalityKeyValue(key));
				}
				else {
					add(model, SPAN_KIND, "LLM");
				}
				add(model, TraceText.INPUT_MESSAGES, inputMessages(model.getRequest().getInstructions()));
				if (model.getResponse() != null) {
					add(model, TraceText.OUTPUT_MESSAGES, outputMessages(model.getResponse()));
				}
			}
			case ToolCallingObservationContext tool -> {
				add(tool, SPAN_KIND, "TOOL");
				add(tool, "gen_ai.tool.name", tool.getToolDefinition().name());
				add(tool, TraceText.TOOL_CALL_ARGUMENTS, tool.getToolCallArguments());
				if (tool.getToolCallResult() != null) {
					add(tool, TraceText.TOOL_CALL_RESULT, tool.getToolCallResult());
				}
			}
			default -> {
			}
		}
		add(context, "retrofit.prompt.name", systemPrompt.name());
		add(context, "retrofit.prompt.version", systemPrompt.version());
		add(context, "session.id", question.conversationId());
		if (question.callerId() != null) {
			add(context, "user.id", question.callerId().toString());
		}
		return context;
	}

	/// What a span knows of the question it belongs to.
	private record Question(String conversationId, @Nullable UUID callerId, boolean llmSpanExported) {
	}

	/// The chat client call this observation is part of, itself or an ancestor, when it is a question of the chat:
	/// only [ChatService] sets the conversation id. Model and tool calls outside the chat get nothing.
	private static @Nullable Question question(Observation.ContextView context) {
		for (Observation.ContextView current = context; current != null; current = parent(current)) {
			if (current instanceof ChatClientObservationContext client
					&& client.getRequest().context().get(ChatMemory.CONVERSATION_ID) instanceof String conversationId) {
				var turn = ChatTurn.find(client.getRequest());
				var llmSpan = !Boolean.FALSE.equals(client.getRequest().context().get(ChatService.LLM_SPANS_EXPORTED));
				return new Question(conversationId, turn == null ? null : turn.caller().id(), llmSpan);
			}
		}
		return null;
	}

	private static Observation.@Nullable ContextView parent(Observation.ContextView context) {
		var parent = context.getParentObservation();
		return parent == null ? null : parent.getContextView();
	}

	private static void add(Observation.Context context, String key, String value) {
		context.addHighCardinalityKeyValue(KeyValue.of(key, value));
	}

	private static void add(Observation.Context context, TraceText text, String value) {
		add(context, text.key().getKey(), value);
	}

	/// The prompt as GenAI semantic convention input messages: a role and its parts.
	private String inputMessages(List<Message> messages) {
		var array = json.createArrayNode();
		for (var message : messages) {
			var parts = array.addObject().put("role", message.getMessageType().getValue()).putArray("parts");
			switch (message) {
				case AssistantMessage assistant -> assistantParts(parts, assistant);
				case ToolResponseMessage tool -> tool.getResponses().forEach(response -> parts.addObject()
						.put("type", "tool_call_response")
						.put("id", response.id())
						.put("response", response.responseData()));
				default -> textPart(parts, message.getText());
			}
		}
		return array.toString();
	}

	/// Each generation as a GenAI semantic convention output message, with its finish reason.
	private String outputMessages(ChatResponse response) {
		var array = json.createArrayNode();
		for (var generation : response.getResults()) {
			var message = array.addObject().put("role", "assistant");
			assistantParts(message.putArray("parts"), generation.getOutput());
			var finishReason = Objects.requireNonNullElse(generation.getMetadata().getFinishReason(), "");
			message.put("finish_reason", finishReason.toLowerCase(Locale.ROOT));
		}
		return array.toString();
	}

	private void assistantParts(ArrayNode parts, AssistantMessage message) {
		textPart(parts, message.getText());
		message.getToolCalls().forEach(call -> parts.addObject()
				.put("type", "tool_call")
				.put("id", call.id())
				.put("name", call.name())
				.set("arguments", arguments(call.arguments())));
	}

	private static void textPart(ArrayNode parts, @Nullable String text) {
		if (text != null && !text.isEmpty()) {
			parts.addObject().put("type", "text").put("content", text);
		}
	}

	/// Arguments that are not a JSON object stay the text the model wrote.
	private JsonNode arguments(String arguments) {
		return ToolCallArguments.parse(json, arguments)
				.<JsonNode>map(object -> object)
				.orElseGet(() -> json.getNodeFactory().stringNode(arguments));
	}

}
