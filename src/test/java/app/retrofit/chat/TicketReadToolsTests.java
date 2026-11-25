package app.retrofit.chat;

import static app.retrofit.chat.ScriptedChatModel.answer;
import static app.retrofit.chat.ScriptedChatModel.toolCall;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import app.retrofit.TestUsers.TestUser;
import app.retrofit.auth.Role;
import tools.jackson.databind.JsonNode;

/// The chat's read tools over the real server, with the [ScriptedChatModel] choosing the tool calls a model would.
class TicketReadToolsTests extends ChatServerContext {

	@Test
	void searchAsACustomerReturnsOnlyTheirOrganizationsTickets() {
		var customer = users.create(Role.USER);
		var outsider = users.create(Role.USER);
		var session = login(customer);
		var own = raiseTicket(session, "Export to PDF fails");
		var foreign = raiseTicket(login(outsider), "Export to PDF fails");
		model.script(toolCall("searchTickets", "{}"), answer("You have one ticket."));

		var reply = ask(session, "Which tickets do we have?");

		assertThat(reply.status()).isEqualTo(HttpStatus.OK);
		assertThat(reply.text()).isEqualTo("You have one ticket.");
		var result = toolResult();
		assertThat(result.get("total").asLong()).isEqualTo(1);
		assertThat(references(result.get("tickets"))).containsExactly(own);
		assertThat(model.toolResults().getFirst()).doesNotContain(foreign).doesNotContain(customer.id().toString());
	}

	@Test
	void anotherOrganizationsTicketIsNotFoundExactlyLikeAnUnknownOne() {
		var customer = users.create(Role.USER);
		var session = login(customer);
		var foreign = raiseTicket(login(users.create(Role.USER)), "Invoice shows the wrong VAT rate");
		var unknown = "RET-999999999";

		var foreignResult = getTicket(session, foreign);
		var unknownResult = getTicket(session, unknown);

		assertThat(foreignResult.properties()).extracting(property -> property.getKey()).containsExactly("message");
		assertThat(foreignResult.get("message").asString().replace(foreign, "RET-x"))
				.isEqualTo(unknownResult.get("message").asString().replace(unknown, "RET-x"))
				.isEqualTo("There is no ticket RET-x you can see.");
		assertThat(foreignResult.toString()).doesNotContain("VAT");
	}

	@Test
	void aCustomersTicketHoldsNoInternalNote() {
		var customer = users.create(Role.USER);
		var support = login(users.create(Role.SUPPORT));
		var session = login(customer);
		var reference = raiseTicket(session, "Gantt export is blank");
		comment(support, reference, "Engineering tracks this as PRJ-8812.", true);
		comment(support, reference, "We are looking into it.", false);

		var asCustomer = getTicket(session, reference);
		var asSupport = getTicket(support, reference);

		assertThat(asCustomer.get("commentCount").asInt()).isEqualTo(1);
		assertThat(asCustomer.get("latestComments").get(0).get("body").asString()).isEqualTo("We are looking into it.");
		assertThat(asCustomer.toString()).doesNotContain("PRJ-8812");
		assertThat(asSupport.get("commentCount").asInt()).isEqualTo(2);
		assertThat(asSupport.toString()).contains("PRJ-8812");
	}

	/// The question is one operation: its context block and every tool call share one lookup.
	@Test
	void aQuestionWithSeveralToolCallsLooksTheCallerUpOnce() {
		var customer = users.create(Role.USER);
		var reference = raiseTicket(login(customer), "Export to PDF fails");
		var support = users.create(Role.SUPPORT);
		var session = login(support);
		model.script(
				toolCall("searchTickets", "{\"organization\":\"" + organizationName(customer) + "\",\"team\":\""
						+ teamName(support) + "\"}"),
				toolCall("getTicket", "{\"reference\":\"" + reference + "\"}"), answer("Done."));
		clearInvocations(callers);

		var reply = ask(session, "What about the PDF export?");

		assertThat(reply.names()).containsSubsequence("tool", "tool", "done");
		assertThat(model.toolResults()).hasSize(2);
		assertThat(JSON.readTree(model.toolResults().getFirst()).has("total")).as("both names resolved").isTrue();
		assertThat(model.toolResults().getLast()).contains(reference);
		verify(callers).current(any(UUID.class));
	}

	@Test
	void aTeamNameIsUnresolvedForACustomer() {
		var session = login(users.create(Role.USER));

		var result = search(session, "{\"team\":\"Billing\"}");

		assertThat(result.get("message").asString()).isEqualTo("No team named 'Billing': you can see no teams.");
	}

	@Test
	void anUnknownOrganizationListsOnlyTheOrganizationsTheUserMaySee() {
		var customer = users.create(Role.USER);
		var session = login(customer);
		raiseTicket(session, "Own ticket");
		var own = organizationName(customer);
		var other = organizationName(users.create(Role.USER));

		var result = search(session, "{\"organization\":\"Globex\"}");

		assertThat(result.get("message").asString())
				.isEqualTo("No organization named 'Globex' among those you can see: " + own + ".")
				.doesNotContain(other);
	}

	@Test
	void anOrganizationNameMatchesIgnoringCase() {
		var customer = users.create(Role.USER);
		var session = login(customer);
		var own = raiseTicket(session, "Own ticket");

		var result = search(session, "{\"organization\":\"" + organizationName(customer).toUpperCase() + "\"}");

		assertThat(references(result.get("tickets"))).containsExactly(own);
	}

	@Test
	void assignedToMeReturnsOnlyTheCallersTickets() {
		var customer = login(users.create(Role.USER));
		var me = users.create(Role.SUPPORT);
		var colleague = users.create(Role.SUPPORT);
		var mine = raiseTicket(customer, "Assigned to me");
		var theirs = raiseTicket(customer, "Assigned to a colleague");
		assign(mine, me);
		assign(theirs, colleague);

		var result = search(login(me), "{\"assignedToMe\":true}");

		assertThat(references(result.get("tickets"))).containsExactly(mine);
	}

	@Test
	void anInstantRangeTheFilterRefusesIsUnresolvedNamingTheParameter() {
		var session = login(users.create(Role.USER));

		var reversed = search(session,
				"{\"createdFrom\":\"2026-10-02T00:00:00Z\",\"createdTo\":\"2026-10-01T00:00:00Z\"}");
		var unreadable = search(session, "{\"deadlineBefore\":\"next Friday\"}");

		assertThat(reversed.get("message").asString()).isEqualTo("createdTo: Must not be before createdFrom.");
		assertThat(unreadable.get("message").asString()).startsWith("deadlineBefore: 'next Friday' is not an ISO-8601");
	}

	@Test
	void aSearchThatShowsEveryMatchSaysNothingOfTheRest() {
		var session = login(users.create(Role.USER));
		raiseTickets(session, 3);

		var result = search(session, "{}");

		assertThat(result.get("shown").asInt()).isEqualTo(3);
		assertThat(result.has("rest")).isFalse();
	}

	@Test
	void aPageOfAtMostTwentyMatchesSaysWhichLimitShowsThemAll() {
		var session = login(users.create(Role.USER));
		raiseTickets(session, 12);

		var result = search(session, "{}");

		assertThat(result.get("shown").asInt()).isEqualTo(10);
		assertThat(result.get("rest").asString()).isEqualTo("This result holds 10 of the 12 matching tickets. "
				+ "Before you answer, call searchTickets again with the same criteria and limit 12, and name only "
				+ "tickets a search returned.");
	}

	@Test
	void aPageOfMoreThanTwentyMatchesSaysToNameOnlyTheFirstOnes() {
		var session = login(users.create(Role.USER));
		raiseTickets(session, 21);

		var result = search(session, "{\"limit\":20}");

		assertThat(result.get("shown").asInt()).isEqualTo(20);
		assertThat(result.get("rest").asString()).isEqualTo("This result holds the first 20 of the 21 matching "
				+ "tickets, and a search returns at most 20. Say that 21 match and that you name the first ones, and "
				+ "name only tickets a search returned.");
	}

	@Test
	void aShortPageOfMoreThanTwentyMatchesSaysWhichLimitShowsTheMost() {
		var session = login(users.create(Role.USER));
		raiseTickets(session, 21);

		var result = search(session, "{}");

		assertThat(result.get("shown").asInt()).isEqualTo(10);
		assertThat(result.get("rest").asString()).isEqualTo("This result holds the first 10 of the 21 matching "
				+ "tickets, and a search returns at most 20. Before you answer, call searchTickets again with the "
				+ "same criteria and limit 20, then say that 21 match and that you name the first ones, and name "
				+ "only tickets a search returned.");
	}

	private void raiseTickets(String session, int count) {
		for (var i = 1; i <= count; i++) {
			raiseTicket(session, "Ticket " + i);
		}
	}

	private JsonNode search(String session, String arguments) {
		model.script(toolCall("searchTickets", arguments), answer("Done."));
		assertThat(ask(session, "Search").names()).last().isEqualTo("done");
		return toolResult();
	}

	private JsonNode getTicket(String session, String reference) {
		model.script(toolCall("getTicket", "{\"reference\":\"" + reference + "\"}"), answer("Done."));
		assertThat(ask(session, "What about " + reference + "?").names()).last().isEqualTo("done");
		return toolResult();
	}

	private JsonNode toolResult() {
		return JSON.readTree(model.toolResults().getFirst());
	}

	private static List<String> references(JsonNode tickets) {
		return tickets.valueStream().map(ticket -> ticket.get("reference").asString()).toList();
	}

	private String organizationName(TestUser customer) {
		return jdbc.sql("SELECT o.name FROM organization o JOIN app_user u ON u.organization_id = o.id WHERE u.id = :id")
				.param("id", customer.id())
				.query(String.class)
				.single();
	}

	private String teamName(TestUser support) {
		return jdbc.sql("SELECT t.name FROM team t JOIN app_user u ON u.team_id = t.id WHERE u.id = :id")
				.param("id", support.id())
				.query(String.class)
				.single();
	}

	/// Assigns through the schema: the assignee's own team, as the assignee foreign key requires.
	private void assign(String reference, TestUser support) {
		jdbc.sql("""
				UPDATE ticket SET team_id = (SELECT team_id FROM app_user WHERE id = :assigneeId), assignee_id = :assigneeId
				WHERE number = :number
				""")
				.param("assigneeId", support.id())
				.param("number", Long.parseLong(reference.substring("RET-".length())))
				.update();
	}

}
