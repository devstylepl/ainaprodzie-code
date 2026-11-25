package app.retrofit.chat;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.model.Generation;

import reactor.core.publisher.Flux;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/// Reports each tool call the model asks for (chat ADR 0004): a [ChatEvent.ToolCallStarted] when the model begins to
/// write it, for a provider that streams the call, and a [ChatEvent.ToolCall] when it is complete. Spring AI filters
/// complete tool calls out of what the caller of `stream()` sees, so this advisor sits inside [ToolCallingAdvisor]'s
/// loop, where it sees every round, and reports to the request's [ChatTurn]. The user reads the arguments with the
/// values of their placeholders, which the turn gives (chat ADR 0009).
final class ToolCallEventsAdvisor implements StreamAdvisor {

	private static final TypeReference<Map<String, Object>> ARGUMENTS = new TypeReference<>() {
	};

	private final JsonMapper json;

	ToolCallEventsAdvisor(JsonMapper json) {
		this.json = json;
	}

	@Override
	public String getName() {
		return "Tool call events";
	}

	/// Inside the tool loop: after [ToolCallingAdvisor#DEFAULT_ORDER] in the chain.
	@Override
	public int getOrder() {
		return ToolCallingAdvisor.DEFAULT_ORDER + 100;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		var turn = ChatTurn.of(request);
		return Flux.defer(() -> {
			var started = new HashSet<String>();
			return chain.nextStream(request).doOnNext(response -> {
				if (response.chatResponse() != null) {
					response.chatResponse()
							.getResults()
							.stream()
							.map(Generation::getOutput)
							.forEach(message -> report(message, started, turn));
				}
			});
		});
	}

	/// A call the model is still writing arrives as partial parts, each with the call's id and name and a fragment
	/// of its arguments, and `started` holds the ids of the response's calls reported so far. Only the name is read:
	/// the fragments are unfinished JSON that may hold placeholders.
	private void report(AssistantMessage message, Set<String> started, ChatTurn turn) {
		for (var part : message.getParts()) {
			if (part instanceof ToolCallPart(var call, var _, var _) && StreamingParts.isPartial(part)
					&& started.add(call.id())) {
				turn.toolStarted(call.name(), started.size() - 1);
			}
		}
		message.getToolCalls().forEach(call -> turn.toolCalled(event(call, turn)));
	}

	private ChatEvent.ToolCall event(AssistantMessage.ToolCall call, ChatTurn turn) {
		return new ChatEvent.ToolCall(call.name(), arguments(call.arguments(), turn));
	}

	/// Arguments that are not a JSON object are sent as none.
	private Map<String, Object> arguments(String arguments, ChatTurn turn) {
		return ToolCallArguments.parse(json, arguments)
				.<Map<String, Object>>map(object -> json.convertValue(
						PseudonymizingToolCallback.restore(object, turn), ARGUMENTS))
				.orElse(Map.of());
	}

}
