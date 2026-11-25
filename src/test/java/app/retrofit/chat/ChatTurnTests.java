package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;

import app.retrofit.auth.Role;
import app.retrofit.ticket.TicketSummary;
import reactor.core.publisher.Sinks;

/// A chat tool run in a [ChatTurn], without the model or the stream: it reads as the turn's caller, the model reads
/// its result pseudonymized, and the tickets it returned reach the turn's events under the position of its call (chat
/// ADR 0002, 0009, 0010).
class ChatTurnTests extends ChatServerContext {

	@Autowired
	TicketReadTools readTools;

	@Test
	void aToolSendsTheTicketsItReadUnderItsCallAndTheModelReadsTheirPeopleAsPlaceholders() {
		var customer = users.create(Role.USER);
		var reference = raiseTicket(login(customer), "Export to PDF fails");
		var events = Sinks.many().unicast().<ChatEvent>onBackpressureBuffer();
		var turn = new ChatTurn(callers.current(customer.id()), new Pseudonyms(), PeopleDirectory.EMPTY, true, events);
		var getTicket = ChatTurn.tool(readTool("getTicket"), JSON);

		var result = getTicket.call("{\"reference\":\"" + reference + "\"}", new ToolContext(turn.toolContext()));
		events.tryEmitComplete();

		assertThat(result).contains(reference, "<PERSON_1>").doesNotContain(TEST_USER);
		assertThat(events.asFlux().collectList().block()).singleElement()
				.isInstanceOfSatisfying(ChatEvent.Tickets.class, tickets -> {
					assertThat(tickets.toolCall()).isZero();
					assertThat(tickets.tickets()).extracting(TicketSummary::reference).containsExactly(reference);
				});
	}

	@Test
	void aToolReadsAsTheTurnsCaller() {
		var reference = raiseTicket(login(users.create(Role.USER)), "Export to PDF fails");
		var events = Sinks.many().unicast().<ChatEvent>onBackpressureBuffer();
		var outsider = new ChatTurn(callers.current(users.create(Role.USER).id()), new Pseudonyms(),
				PeopleDirectory.EMPTY, true, events);
		var getTicket = ChatTurn.tool(readTool("getTicket"), JSON);

		var result = getTicket.call("{\"reference\":\"" + reference + "\"}", new ToolContext(outsider.toolContext()));
		events.tryEmitComplete();

		assertThat(result).contains("There is no ticket " + reference + " you can see.");
		assertThat(events.asFlux().collectList().block()).isEmpty();
	}

	private ToolCallback readTool(String name) {
		return Arrays.stream(MethodToolCallbackProvider.builder().toolObjects(readTools).build().getToolCallbacks())
				.filter(tool -> tool.getToolDefinition().name().equals(name))
				.findFirst()
				.orElseThrow();
	}

}
