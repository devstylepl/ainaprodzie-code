package app.retrofit.chat;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import app.retrofit.organization.Caller;
import app.retrofit.ticket.TicketSummary;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.json.JsonMapper;

/// One question's turn, as its tools and [ToolCallEventsAdvisor] see it: the signed-in user the tools read as, looked
/// up once when the question arrived and never named by the model (chat ADR 0002), the conversation's placeholders
/// (chat ADR 0009) and the events the user reads beside the answer (chat ADR 0004, 0010). [ChatService] creates it and
/// puts it into the tool context, where the advisor finds it too, through the request's options.
///
/// Every text crosses between the model and the people's names here: [#forModel(String)] for what the model reads,
/// the question and the tool results, and [#fromModel] for what it wrote, which the user reads. Whether a person
/// gets a placeholder in this question (chat ADR 0018) is decided here; [ChatService] only spares the read of a
/// directory that no text would be matched against.
///
/// It numbers the tool calls (chat ADR 0010): each tool built by [#tool] counts its call before it runs, and Spring
/// AI runs the calls in the order the advisor reports them, so a call's number is the position of its `tool` event,
/// and a tool sends the tickets it returned under the number of the call that is running.
final class ChatTurn {

	private static final String KEY = "retrofit.chat.turn";

	private static final Logger log = LoggerFactory.getLogger(ChatTurn.class);

	private final Caller caller;

	private final Pseudonyms pseudonyms;

	private final PeopleDirectory people;

	private final boolean protectsPersonalData;

	private final Sinks.Many<ChatEvent> events;

	private final AtomicInteger calls = new AtomicInteger();

	/// `protectsPersonalData` false gives no person a placeholder (chat ADR 0018).
	/// `people` are the people the caller may see, whom a placeholder stands for wherever the model reads them.
	ChatTurn(Caller caller, Pseudonyms pseudonyms, PeopleDirectory people, boolean protectsPersonalData,
			Sinks.Many<ChatEvent> events) {
		this.caller = caller;
		this.pseudonyms = pseudonyms;
		this.people = people;
		this.protectsPersonalData = protectsPersonalData;
		this.events = events;
	}

	static ChatTurn of(ToolContext toolContext) {
		return required(find(toolContext.getContext()));
	}

	/// The turn in the tool context of the request's options.
	static ChatTurn of(ChatClientRequest request) {
		return required(find(request));
	}

	/// The turn in the tool context of the request's options, or none, for a reader that must not fail the request it
	/// reads, such as a trace.
	static @Nullable ChatTurn find(ChatClientRequest request) {
		return request.prompt().getOptions() instanceof ToolCallingChatOptions options
				? find(options.getToolContext()) : null;
	}

	private static @Nullable ChatTurn find(@Nullable Map<String, Object> toolContext) {
		return toolContext != null && toolContext.get(KEY) instanceof ChatTurn turn ? turn : null;
	}

	private static ChatTurn required(@Nullable ChatTurn turn) {
		if (turn == null) {
			throw new IllegalStateException("The tool context carries no " + KEY);
		}
		return turn;
	}

	/// `tool` as the model calls it in a turn: counted before it runs, a call whose arguments cannot be bound
	/// included, and with its arguments and result going through the conversation's placeholders.
	static ToolCallback tool(ToolCallback tool, JsonMapper json) {
		return new Counting(new PseudonymizingToolCallback(tool, json));
	}

	/// The tool context that carries this turn.
	Map<String, Object> toolContext() {
		return Map.of(KEY, this);
	}

	Caller caller() {
		return caller;
	}

	/// A text as the model reads it, the question or a text of a tool result: with the people the conversation and
	/// the directory know as placeholders, when the question protects personal data. A text that only looks like a
	/// placeholder gets one of its own either way, so [#fromModel] never takes it for a person (chat ADR 0009).
	String forModel(String text) {
		return protectsPersonalData ? pseudonyms.pseudonymize(text, people) : pseudonyms.pseudonymizeLookalikes(text);
	}

	/// The texts of a tool result as the model reads them, with `namedPeople`, the people the result names in its own
	/// fields, as persons when the question protects personal data.
	List<String> forModel(List<String> texts, Collection<String> namedPeople) {
		if (protectsPersonalData) {
			namedPeople.forEach(pseudonyms::person);
		}
		return texts.stream().map(this::forModel).toList();
	}

	/// A text the model wrote, as the user reads it and as a tool gets it in its arguments: with the value of every
	/// placeholder the conversation gave out, whether this question protects personal data or an earlier one did.
	String fromModel(String text) {
		return pseudonyms.restore(text);
	}

	/// The model began to write a tool call, the `ordinal`-th of the response it is streaming, counted from 0. The
	/// calls of the earlier responses have all run, so they give the number of the response's first call.
	void toolStarted(String tool, int ordinal) {
		send(new ChatEvent.ToolCallStarted(tool, calls.get() + ordinal));
	}

	void toolCalled(ChatEvent.ToolCall call) {
		send(call);
	}

	/// The tickets the running tool call returned.
	void tickets(List<TicketSummary> tickets, @Nullable TicketListQuery query) {
		send(new ChatEvent.Tickets(calls.get() - 1, tickets, query));
	}

	/// A client that left cancels the sink, and its events have nowhere to go; any other refusal loses what the event
	/// would have shown beside the answer.
	private void send(ChatEvent event) {
		var result = events.tryEmitNext(event);
		if (result.isFailure() && result != Sinks.EmitResult.FAIL_CANCELLED) {
			log.warn("A {} event was not sent to the chat: {}", event.type(), result);
		}
	}

	private static final class Counting implements ToolCallback {

		private final ToolCallback tool;

		Counting(ToolCallback tool) {
			this.tool = tool;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return tool.getToolDefinition();
		}

		@Override
		public ToolMetadata getToolMetadata() {
			return tool.getToolMetadata();
		}

		@Override
		public String call(String toolInput) {
			return tool.call(toolInput);
		}

		@Override
		public String call(String toolInput, ToolContext toolContext) {
			of(toolContext).calls.incrementAndGet();
			return tool.call(toolInput, toolContext);
		}

	}

}
