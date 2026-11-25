package app.retrofit.chat;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

import app.retrofit.ticket.TicketSummary;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/// One event of a streamed answer (chat ADR 0004): `reasoning` for each piece of the model's reasoning (chat ADR
/// 0020), `toolStarted` when the model begins to write a tool call, `tool` when it calls the tool, `tickets` when a
/// tool returned tickets (chat ADR 0010), `token` for each piece of the answer, then `done`, or `error` in its place.
/// The SSE event name and the `type` property are the same word.
@Schema(name = "ChatEvent", discriminatorProperty = "type",
		oneOf = { ChatEvent.Reasoning.class, ChatEvent.ToolCallStarted.class, ChatEvent.ToolCall.class,
				ChatEvent.Tickets.class, ChatEvent.Token.class, ChatEvent.Done.class, ChatEvent.Error.class },
		discriminatorMapping = {
				@DiscriminatorMapping(value = ChatEvent.REASONING, schema = ChatEvent.Reasoning.class),
				@DiscriminatorMapping(value = ChatEvent.TOOL_STARTED, schema = ChatEvent.ToolCallStarted.class),
				@DiscriminatorMapping(value = ChatEvent.TOOL, schema = ChatEvent.ToolCall.class),
				@DiscriminatorMapping(value = ChatEvent.TICKETS, schema = ChatEvent.Tickets.class),
				@DiscriminatorMapping(value = ChatEvent.TOKEN, schema = ChatEvent.Token.class),
				@DiscriminatorMapping(value = ChatEvent.DONE, schema = ChatEvent.Done.class),
				@DiscriminatorMapping(value = ChatEvent.ERROR, schema = ChatEvent.Error.class) })
sealed interface ChatEvent {

	String REASONING = "reasoning";

	String TOOL_STARTED = "toolStarted";

	String TOOL = "tool";

	String TICKETS = "tickets";

	String TOKEN = "token";

	String DONE = "done";

	String ERROR = "error";

	/// The discriminator, which is also the SSE event name.
	String type();

	/// The next piece of the model's reasoning, as the provider wrote it. Consecutive ones form one block, which any
	/// other event ends.
	@Schema(name = "ChatReasoningEvent")
	record Reasoning(@Schema(requiredMode = REQUIRED) String text) implements ChatEvent {

		@Override
		@JsonProperty
		@Schema(requiredMode = REQUIRED, allowableValues = REASONING)
		public String type() {
			return REASONING;
		}

	}

	/// The model began to write a call of `tool` and has not finished its arguments. `toolCall` is the position the
	/// call's `tool` event will have among the turn's `tool` events; a call the model never finishes has none.
	@Schema(name = "ChatToolCallStartedEvent")
	record ToolCallStarted(@Schema(requiredMode = REQUIRED, example = "searchTickets") String tool,
			@Schema(requiredMode = REQUIRED, description = "Counts from 0") int toolCall) implements ChatEvent {

		@Override
		@JsonProperty
		@Schema(requiredMode = REQUIRED, allowableValues = TOOL_STARTED)
		public String type() {
			return TOOL_STARTED;
		}

	}

	/// The model calls `tool` with the arguments it chose. The signed-in user's id is never one of them, and the
	/// tool's result is never sent.
	@Schema(name = "ChatToolCallEvent")
	record ToolCall(@Schema(requiredMode = REQUIRED, example = "searchTickets") String tool,
			@Schema(requiredMode = REQUIRED, description = "The arguments as the model wrote them")
			Map<String, Object> arguments) implements ChatEvent {

		@Override
		@JsonProperty
		@Schema(requiredMode = REQUIRED, allowableValues = TOOL)
		public String type() {
			return TOOL;
		}

	}

	/// The tickets a tool call returned, as the user reads them in the ticket list and never as the model read them
	/// (chat ADR 0010). `toolCall` is the position of that call's `tool` event among the turn's `tool` events; a
	/// search also has the `query` that lists the same tickets.
	@Schema(name = "ChatTicketsEvent")
	record Tickets(@Schema(requiredMode = REQUIRED, description = "Counts from 0") int toolCall,
			@Schema(requiredMode = REQUIRED) List<TicketSummary> tickets,
			@Schema(requiredMode = REQUIRED, nullable = true) @Nullable TicketListQuery query) implements ChatEvent {

		@Override
		@JsonProperty
		@Schema(requiredMode = REQUIRED, allowableValues = TICKETS)
		public String type() {
			return TICKETS;
		}

	}

	/// The next piece of the answer text, Markdown written by the model.
	@Schema(name = "ChatTokenEvent")
	record Token(@Schema(requiredMode = REQUIRED) String text) implements ChatEvent {

		@Override
		@JsonProperty
		@Schema(requiredMode = REQUIRED, allowableValues = TOKEN)
		public String type() {
			return TOKEN;
		}

	}

	/// The answer is complete; the conversation remembers this turn.
	@Schema(name = "ChatDoneEvent")
	record Done() implements ChatEvent {

		@Override
		@JsonProperty
		@Schema(requiredMode = REQUIRED, allowableValues = DONE)
		public String type() {
			return DONE;
		}

	}

	/// The answer failed after the stream started; the conversation remembers nothing of this turn.
	@Schema(name = "ChatErrorEvent")
	record Error(@Schema(requiredMode = REQUIRED) ChatErrorCode code) implements ChatEvent {

		@Override
		@JsonProperty
		@Schema(requiredMode = REQUIRED, allowableValues = ERROR)
		public String type() {
			return ERROR;
		}

	}

}
