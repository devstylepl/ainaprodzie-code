package app.retrofit.chat;

import static app.retrofit.chat.ScriptedChatModel.answer;
import static app.retrofit.chat.ScriptedChatModel.toolCall;
import static app.retrofit.chat.ScriptedChatModel.toolCalls;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import app.retrofit.TestUsers.TestUser;
import app.retrofit.auth.Role;
import tools.jackson.databind.JsonNode;

/// The `tickets` event: the tickets a tool call returned, sent to the user as they read them in the ticket list,
/// beside the answer the model writes (chat ADR 0010).
class ChatTicketsEventTests extends ChatServerContext {

	@Autowired
	ChatSettingStore settings;

	@BeforeEach
	void protectPersonalDataInTheApp() {
		settings.change(new ChatSettingsChange(false, false, true, true, "chat-gemini", null, null));
	}

	@Test
	void aSearchSendsTheTicketsItFoundWithTheNamesTheModelReadsAsPlaceholders() {
		var session = login(users.create(Role.USER));
		var reference = raiseTicket(session, "Export to PDF fails");
		model.script(toolCall("searchTickets", "{}"), answer("You have " + reference + "."));

		var reply = ask(session, "Which tickets do we have?");

		assertThat(reply.names()).startsWith("tool", "tickets").endsWith("done");
		var event = reply.events("tickets").getFirst().data();
		assertThat(event.get("toolCall").asInt()).isZero();
		var ticket = event.get("tickets").get(0);
		assertThat(ticket.get("reference").asString()).isEqualTo(reference);
		assertThat(ticket.get("title").asString()).isEqualTo("Export to PDF fails");
		assertThat(ticket.get("status").asString()).isEqualTo("NEW");
		assertThat(ticket.get("author").get("name").asString()).isEqualTo(TEST_USER);
		assertThat(ticket.get("sla").get("breached").asBoolean()).isFalse();
		assertThat(model.toolResults().getFirst()).contains(reference).doesNotContain(TEST_USER);
	}

	/// The model searches twice in one response, and the first search's arguments cannot be bound: Spring AI reports
	/// both calls before it runs either, and the tickets name the second.
	@Test
	void theTicketsNameTheirToolCallByThePositionOfItsToolEvent() {
		var session = login(users.create(Role.USER));
		var reference = raiseTicket(session, "Export to PDF fails");
		model.script(toolCalls("searchTickets", "{\"statuses\":[\"SOMEDAY\"]}", "{}"),
				answer("You have " + reference + "."));

		var reply = ask(session, "Which tickets do we have?");

		assertThat(reply.names()).startsWith("tool", "tool", "tickets").endsWith("done");
		assertThat(reply.names()).containsOnlyOnce("tickets");
		assertThat(reply.events("tickets").getFirst().data().get("toolCall").asInt()).isEqualTo(1);
	}

	/// Every criterion of the search, with the names resolved to the ids the tool searched by and "assigned to me" as
	/// the caller's id, is the `GET /api/tickets` query that lists the same tickets.
	@Test
	void aSearchSendsTheTicketListQueryThatRepeatsIt() {
		var customer = users.create(Role.USER);
		var support = users.create(Role.SUPPORT);
		var session = login(support);
		var reference = raiseTicket(login(customer), "Export to PDF fails");
		classifyAndAssign(reference, support);
		var organizationId = affiliation("organization_id", customer);
		var teamId = affiliation("team_id", support);
		model.script(toolCall("searchTickets", """
				{"text":"PDF","statuses":["NEW"],"categories":["BUG"],"priorities":["HIGH"],"slaBreached":false,
				"assignedToMe":true,"organization":"%s","team":"%s"}""".formatted(name("organization", organizationId),
				name("team", teamId))), answer(reference + " is."));

		var reply = ask(session, "Which of my new PDF bugs are within the SLA?");

		var query = tickets(reply).get("query");
		assertThat(query.get("text").asString()).isEqualTo("PDF");
		assertThat(query.get("status").valueStream().map(JsonNode::asString)).containsExactly("NEW");
		assertThat(query.get("category").valueStream().map(JsonNode::asString)).containsExactly("BUG");
		assertThat(query.get("priority").valueStream().map(JsonNode::asString)).containsExactly("HIGH");
		assertThat(query.get("organizationId").asString()).isEqualTo(organizationId.toString());
		assertThat(query.get("teamId").asString()).isEqualTo(teamId.toString());
		assertThat(query.get("assigneeId").asString()).isEqualTo(support.id().toString());
		assertThat(query.get("slaBreached").asBoolean()).isFalse();
		assertThat(query.get("authorId").isNull()).isTrue();
		assertThat(query.get("sort").asString()).isEqualTo("RECENTLY_UPDATED");
	}

	@Test
	void aTicketReadByItsReferenceIsSentAloneWithoutAQuery() {
		var session = login(users.create(Role.USER));
		var reference = raiseTicket(session, "Export to PDF fails");
		raiseTicket(session, "Gantt export is blank");
		model.script(toolCall("getTicket", "{\"reference\":\"" + reference + "\"}"), answer(reference + " is new."));

		var event = tickets(ask(session, "What about " + reference + "?"));

		assertThat(event.get("toolCall").asInt()).isZero();
		assertThat(event.get("tickets").valueStream().map(ticket -> ticket.get("reference").asString()))
				.containsExactly(reference);
		assertThat(event.get("query").isNull()).isTrue();
	}

	@Test
	void aTicketNotFoundASearchWithAnUnresolvedNameOrOneThatFindsNothingSendsNoTickets() {
		var session = login(users.create(Role.USER));
		raiseTicket(session, "Export to PDF fails");
		model.script(toolCall("getTicket", "{\"reference\":\"RET-999999999\"}"),
				toolCall("searchTickets", "{\"organization\":\"Globex\"}"),
				toolCall("searchTickets", "{\"text\":\"Gantt\"}"), answer("Nothing found."));

		var reply = ask(session, "What about RET-999999999, Globex or the Gantt chart?");

		assertThat(reply.names()).startsWith("tool", "tool", "tool", "token").doesNotContain("tickets");
	}

	private static JsonNode tickets(Reply reply) {
		return reply.events("tickets").getFirst().data();
	}

	/// A high-priority bug assigned through the schema: the assignee's own team, as the assignee foreign key requires.
	private void classifyAndAssign(String reference, TestUser support) {
		jdbc.sql("""
				UPDATE ticket SET category = 'BUG', priority = 'HIGH', assignee_id = :assigneeId,
				    team_id = (SELECT team_id FROM app_user WHERE id = :assigneeId)
				WHERE number = :number
				""")
				.param("assigneeId", support.id())
				.param("number", Long.parseLong(reference.substring("RET-".length())))
				.update();
	}

	private UUID affiliation(String column, TestUser user) {
		return jdbc.sql("SELECT " + column + " FROM app_user WHERE id = :id")
				.param("id", user.id())
				.query(UUID.class)
				.single();
	}

	private String name(String table, UUID id) {
		return jdbc.sql("SELECT name FROM " + table + " WHERE id = :id").param("id", id).query(String.class).single();
	}

}
