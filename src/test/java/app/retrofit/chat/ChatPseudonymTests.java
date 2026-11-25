package app.retrofit.chat;

import static app.retrofit.chat.ScriptedChatModel.answer;
import static app.retrofit.chat.ScriptedChatModel.chunks;
import static app.retrofit.chat.ScriptedChatModel.toolCall;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import app.retrofit.TestUsers.TestUser;
import app.retrofit.auth.Role;
import app.retrofit.chat.ChatTracingTestConfiguration.StubPersonalDataMasker;
import tools.jackson.databind.JsonNode;

/// The model reads the people the app knows as numbered placeholders, and the user reads them back in full (chat ADR
/// 0009). The people come from the app's own data: the fields of a tool result and the directory of the people the
/// asker may see. The stub stands in for the Presidio of the app's spans, which the question's path never calls.
class ChatPseudonymTests extends ChatServerContext {

	private static final String NAME = "Jan Kowalski";

	@Autowired
	StubPersonalDataMasker presidio;

	@Autowired
	ChatSettingStore settings;

	@BeforeEach
	void protectPersonalDataInTheApp() {
		settings.change(new ChatSettingsChange(false, false, true, true, "chat-gemini", null, null));
	}

	@AfterEach
	void resetPresidio() {
		presidio.failing(false);
	}

	@Test
	void theModelReadsTheFullNameOfAUserInTheQuestionAsAPlaceholder() {
		users.create(Role.USER, NAME);
		var session = login(users.create(Role.SUPPORT));
		model.script(answer("Noted."));

		ask(session, "Who is " + NAME + "?");

		assertThat(model.prompts().getFirst().getUserMessage().getText()).endsWith("Who is <PERSON_1>?")
				.doesNotContain(NAME);
	}

	@Test
	void anEmailAddressInTheQuestionReachesTheModelAsWritten() {
		var session = login(users.create(Role.USER));
		model.script(answer("Noted."));

		ask(session, "Search for jan.kowalski@acme.example");

		assertThat(model.prompts().getFirst().getUserMessage().getText())
				.endsWith("Search for jan.kowalski@acme.example");
	}

	@Test
	void anEmailAddressInATicketsDescriptionReachesTheModelAsWrittenAndItsAuthorIsAPlaceholder() {
		var session = login(users.create(Role.USER));
		var reference = raiseTicket(session, "Remove an account", "Remove jan.kowalski@acme.example please");
		model.script(toolCall("getTicket", "{\"reference\":\"" + reference + "\"}"), answer("Done."));

		ask(session, "What does " + reference + " say?");

		assertThat(model.toolResults()).singleElement().asString()
				.contains("\"description\":\"Remove jan.kowalski@acme.example please\"", "\"author\":\"<PERSON_1>\"")
				.doesNotContain("<EMAIL_ADDRESS");
	}

	/// The gateway's guardrail fails closed when Presidio does not answer (chat ADR 0017); the app calls none.
	@Test
	void aQuestionIsAnsweredWhilePresidioDoesNotAnswer() {
		presidio.failing(true);
		var session = login(users.create(Role.USER));
		raiseTicket(session, "Printer jam");
		model.script(toolCall("searchTickets", "{}"), answer("One ticket."));

		var reply = ask(session, "Which tickets do we have?");

		assertThat(reply.names()).last().isEqualTo("done");
		assertThat(reply.text()).isEqualTo("One ticket.");
	}

	/// Without the switch, a question and a tool result reach the model as written, and a user's name is only a name.
	@Test
	void withTheSwitchOffTheModelReadsTheQuestionAndTheToolResultAsWritten() {
		leavePersonalDataToTheGateway();
		var session = login(users.create(Role.USER, NAME));
		var reference = raiseTicket(session, "Call " + NAME + " back");
		model.script(toolCall("getTicket", "{\"reference\":\"" + reference + "\"}"), answer("Call them back."));

		var reply = ask(session, "What does " + reference + " say about " + NAME + "?");

		assertThat(reply.names()).last().isEqualTo("done");
		assertThat(model.prompts().getFirst().getUserMessage().getText()).endsWith(NAME + "?");
		assertThat(model.toolResults()).singleElement().asString().contains("\"Call " + NAME + " back\"")
				.doesNotContain("<PERSON");
	}

	/// The switch turned off during a conversation changes what the model reads from then on and nothing it has read:
	/// memory still names the person by their placeholder, so a tool gets the name for it.
	@Test
	void afterTheSwitchIsTurnedOffAToolStillSearchesForTheValueOfAPlaceholderOfTheConversation() {
		var session = login(users.create(Role.USER, NAME));
		var reference = raiseTicket(session, "Call " + NAME + " back");
		var conversation = UUID.randomUUID();
		model.script(answer("Noted."));
		ask(session, conversation, "I am " + NAME + ".");
		leavePersonalDataToTheGateway();
		model.script(toolCall("searchTickets", "{\"text\":\"<PERSON_1>\"}"), answer("One ticket."));

		var reply = ask(session, conversation, "Which tickets name " + NAME + "?");

		assertThat(model.prompts().getFirst().getUserMessage().getText()).endsWith("Which tickets name " + NAME + "?");
		assertThat(reply.events("tool").getFirst().data().get("arguments").get("text").asString()).isEqualTo(NAME);
		assertThat(model.toolResults()).singleElement().asString().contains(reference);
	}

	/// A text that only looks like a placeholder is a value of its own without the switch too, so what the model
	/// writes is read back in one way in both states.
	@Test
	void withTheSwitchOffAToolSearchesForATextOfTheQuestionThatLooksLikeAPlaceholderAsItIs() {
		leavePersonalDataToTheGateway();
		var session = login(users.create(Role.USER, NAME));
		var reference = raiseTicket(session, "Log line <PERSON_1>");
		model.script(toolCall("searchTickets", "{\"text\":\"<PLACEHOLDER_1>\"}"),
				answer("<PLACEHOLDER_1> is in one ticket."));

		var reply = ask(session, "Which tickets quote <PERSON_1>, " + NAME + " asks?");

		assertThat(model.prompts().getFirst().getUserMessage().getText())
				.endsWith("Which tickets quote <PLACEHOLDER_1>, " + NAME + " asks?");
		assertThat(reply.events("tool").getFirst().data().get("arguments").get("text").asString())
				.isEqualTo("<PERSON_1>");
		assertThat(model.toolResults()).singleElement().asString().contains(reference);
		assertThat(reply.text()).isEqualTo("<PERSON_1> is in one ticket.");
	}

	@Test
	void withTheSwitchOffTheModelReadsATextOfATicketThatLooksLikeAPlaceholderAsAValueOfItsOwn() {
		leavePersonalDataToTheGateway();
		var session = login(users.create(Role.USER, NAME));
		var reference = raiseTicket(session, "Log line <PERSON_1>");
		model.script(toolCall("getTicket", "{\"reference\":\"" + reference + "\"}"),
				answer("It quotes <PLACEHOLDER_1>."));

		var reply = ask(session, "What does " + reference + " quote?");

		assertThat(model.toolResults()).singleElement().asString()
				.contains("\"Log line <PLACEHOLDER_1>\"", "\"author\":\"" + NAME + "\"");
		assertThat(reply.text()).isEqualTo("It quotes <PERSON_1>.");
	}

	@Test
	void aNameTypedBeforeAnyToolResultIsThePlaceholderOfTheResultsAuthor() {
		var author = users.create(Role.USER, "Ian Chu");
		var session = login(users.create(Role.SUPPORT));
		var reference = raiseTicket(login(author), "Cannot sign in");
		model.script(toolCall("getTicket", "{\"reference\":\"" + reference + "\"}"), answer("Yes."));

		ask(session, "Is Ian Chu the author of " + reference + "?");

		assertThat(model.prompts().getFirst().getUserMessage().getText())
				.endsWith("Is <PERSON_1> the author of " + reference + "?");
		assertThat(model.toolResults()).singleElement().asString().contains("\"author\":\"<PERSON_1>\"");
	}

	@Test
	void aNameInAPolishCaseIsThePlaceholderOfTheResultsAssignee() {
		var assignee = users.create(Role.SUPPORT, "Ben Barker");
		var session = login(users.create(Role.SUPPORT));
		var reference = raiseTicket(login(users.create(Role.USER)), "Cannot sign in");
		assign(reference, assignee);
		model.script(toolCall("getTicket", "{\"reference\":\"" + reference + "\"}"), answer("Tak."));

		ask(session, "Czy zgłoszenie " + reference + " jest przypisane do Bena Barkera?");

		var prompt = model.prompts().getFirst().getUserMessage().getText();
		var assigneePlaceholder = ticket().get("summary").get("assignee").asString();
		assertThat(assigneePlaceholder).matches("<PERSON_\\d+>");
		assertThat(prompt).endsWith("jest przypisane do " + assigneePlaceholder + "?");
	}

	@Test
	void aUserNamedInADescriptionOrACommentIsAPlaceholderThereToo() {
		users.create(Role.USER, "Ola Kowal");
		var assignee = users.create(Role.SUPPORT, "Ben Barker");
		var customer = login(users.create(Role.USER));
		var reference = raiseTicket(customer, "Cannot sign in", "Please ask Ola Kowal to confirm");
		assign(reference, assignee);
		var session = login(users.create(Role.SUPPORT));
		comment(session, reference, "Przekazałem Benowi Barkerowi", false);
		model.script(toolCall("getTicket", "{\"reference\":\"" + reference + "\"}"), answer("Done."));

		ask(session, "What does " + reference + " say?");

		var ticket = ticket();
		assertThat(ticket.get("description").asString()).matches("Please ask <PERSON_\\d+> to confirm");
		var assigneePlaceholder = ticket.get("summary").get("assignee").asString();
		assertThat(ticket.get("latestComments").get(0).get("body").asString())
				.isEqualTo("Przekazałem " + assigneePlaceholder);
		assertThat(model.toolResults().getFirst()).doesNotContain("Ola Kowal", "Ben Barker", "Benowi");
	}

	/// "Sam" and "Ben" are ordinary words, so one part of a name is not a person.
	@Test
	void onePartOfANameAloneReachesTheModelAsWritten() {
		users.create(Role.SUPPORT, "Sam Support");
		var session = login(users.create(Role.SUPPORT));
		model.script(answer("Nie wiem."));

		ask(session, "Sam to zgłosił?");

		assertThat(model.prompts().getFirst().getUserMessage().getText()).endsWith("Sam to zgłosił?");
	}

	@Test
	void aDisplayNameOfOneWordIsAPlaceholderOnlyWhereAResultsFieldNamesIt() {
		var author = users.create(Role.USER, "Madonna");
		var session = login(users.create(Role.SUPPORT));
		var reference = raiseTicket(login(author), "Madonna called twice");
		model.script(toolCall("getTicket", "{\"reference\":\"" + reference + "\"}"), answer("Yes."));

		ask(session, "Czy Madonna to zgłosiła, " + reference + "?");

		assertThat(model.prompts().getFirst().getUserMessage().getText()).endsWith("Czy Madonna to zgłosiła, "
				+ reference + "?");
		assertThat(model.toolResults()).singleElement().asString().contains("\"author\":\"<PERSON_1>\"",
				"\"<PERSON_1> called twice\"");
	}

	@Test
	void aCustomerDoesNotSeeAUserOfAnotherOrganization() {
		users.create(Role.USER, "Olga Zielińska");
		var session = login(users.create(Role.USER));
		model.script(answer("Nie wiem."));

		ask(session, "Co napisała Olga Zielińska?");

		assertThat(model.prompts().getFirst().getUserMessage().getText()).endsWith("Co napisała Olga Zielińska?");
	}

	@Test
	void aCustomerSeesTheSupportStaffAndTheirOwnOrganization() {
		users.create(Role.SUPPORT, "Ewa Kamińska");
		var customer = users.create(Role.USER, "Hanna Wójcik");
		var session = login(customer);
		model.script(answer("Nie wiem."));

		ask(session, "Czy Ewa Kamińska odpisała Hannie Wójcik?");

		assertThat(model.prompts().getFirst().getUserMessage().getText())
				.endsWith("Czy <PERSON_1> odpisała <PERSON_2>?");
	}

	@Test
	void theUserReadsTheAnswersPlaceholdersAsTheirValues() {
		users.create(Role.USER, NAME);
		var session = login(users.create(Role.SUPPORT));
		model.script(answer("<PERSON_1> raised it."));

		var reply = ask(session, "Who is " + NAME + "?");

		assertThat(reply.text()).isEqualTo(NAME + " raised it.");
	}

	@Test
	void aPlaceholderCutBetweenTwoChunksReachesTheUserAsItsValue() {
		users.create(Role.USER, NAME);
		var session = login(users.create(Role.SUPPORT));
		model.script(chunks("Ask <PER", "SON\\", "_1> today", "."));

		var reply = ask(session, "Who is " + NAME + "?");

		assertThat(reply.text()).isEqualTo("Ask " + NAME + " today.");
	}

	/// Models escape an underscore in Markdown, as they did with codes such as `WAITING\_FOR\_CUSTOMER`.
	@Test
	void aPlaceholderWithAnEscapedUnderscoreReachesTheUserAsItsValue() {
		users.create(Role.USER, NAME);
		var session = login(users.create(Role.SUPPORT));
		model.script(answer("Ask <PERSON\\_1> today."));

		var reply = ask(session, "Who is " + NAME + "?");

		assertThat(reply.text()).isEqualTo("Ask " + NAME + " today.");
	}

	/// A question may quote a text that only looks like a placeholder, such as a line of a log the user pasted.
	@Test
	void theUserReadsATextOfTheQuestionThatLooksLikeAPlaceholderAsItIs() {
		users.create(Role.USER, NAME);
		var session = login(users.create(Role.SUPPORT));
		model.script(answer("<PLACEHOLDER_1> is not <PERSON_1>."));

		var reply = ask(session, "Is " + NAME + " the <PERSON_1> of this log?");

		assertThat(model.prompts().getFirst().getUserMessage().getText())
				.endsWith("Is <PERSON_1> the <PLACEHOLDER_1> of this log?");
		assertThat(reply.text()).isEqualTo("<PERSON_1> is not " + NAME + ".");
	}

	/// A ticket may quote one too, and the model may pass it on to a tool.
	@Test
	void aToolSearchesForATextOfATicketThatLooksLikeAPlaceholderAsItIs() {
		var session = login(users.create(Role.USER, NAME));
		var reference = raiseTicket(session, "Log line <PERSON_1>");
		model.script(toolCall("getTicket", "{\"reference\":\"" + reference + "\"}"),
				toolCall("searchTickets", "{\"text\":\"<PLACEHOLDER_1>\"}"), answer("One ticket."));

		var reply = ask(session, "Which tickets quote the log of " + reference + ", and is it " + NAME + "?");

		assertThat(reply.events("tool").get(1).data().get("arguments").get("text").asString()).isEqualTo("<PERSON_1>");
		assertThat(model.toolResults().getLast()).contains(reference);
	}

	@Test
	void aToolSearchesForTheValueOfAPlaceholderTheModelPassesIt() {
		var session = login(users.create(Role.USER, NAME));
		var reference = raiseTicket(session, "Call " + NAME + " back");
		model.script(toolCall("searchTickets", "{\"text\":\"<PERSON_1>\"}"), answer("One ticket."));

		ask(session, "Which tickets name " + NAME + "?");

		assertThat(model.toolResults()).singleElement().asString().contains(reference);
	}

	@Test
	void theUserReadsTheToolArgumentsWithTheValuesOfTheirPlaceholders() {
		users.create(Role.USER, NAME);
		var session = login(users.create(Role.SUPPORT));
		model.script(toolCall("searchTickets", "{\"text\":\"<PERSON_1>\"}"), answer("No tickets."));

		var reply = ask(session, "Which tickets name " + NAME + "?");

		assertThat(reply.events().getFirst().data().get("arguments").get("text").asString()).isEqualTo(NAME);
	}

	/// Memory keeps what the model read and wrote, so a later turn sends the model no names either.
	@Test
	void aConversationKeepsItsPlaceholdersFromTurnToTurn() {
		users.create(Role.USER, "Anna Nowak");
		users.create(Role.USER, NAME);
		var session = login(users.create(Role.SUPPORT));
		var conversation = UUID.randomUUID();
		model.script(answer("<PERSON_1> is a customer."));
		ask(session, conversation, "Who is Anna Nowak?");

		model.script(answer("Unknown."));
		ask(session, conversation, "And " + NAME + "?");

		var messages = model.prompts().getFirst().getInstructions();
		assertThat(messages.get(1).getText()).isEqualTo("Who is <PERSON_1>?");
		assertThat(messages.get(2).getText()).isEqualTo("<PERSON_1> is a customer.");
		assertThat(messages.getLast().getText()).endsWith("And <PERSON_2>?");
	}

	/// No directory is needed for it: the author is a person because the result names them in its author field.
	@Test
	void aPersonATicketNamesInItsOwnFieldsIsAPlaceholderThereAndInItsText() {
		var session = login(users.create(Role.USER));
		var reference = raiseTicket(session, "Ask " + TEST_USER + " about it");
		model.script(toolCall("getTicket", "{\"reference\":\"" + reference + "\"}"), answer("Ask them."));

		ask(session, "What does " + reference + " say?");

		assertThat(model.toolResults()).singleElement().asString()
				.contains("\"author\":\"<PERSON_1>\"", "\"Ask <PERSON_1> about it\"")
				.doesNotContain(TEST_USER);
	}

	/// The demo's question, followed through the four stages of one person: the name on the ticket's page, its
	/// placeholder in what the model reads, the same placeholder in what the model writes, and the name in what the
	/// user reads. The person to remove has no account, as Jan Kowalski has none in the demo, so he is a name at every
	/// stage. He is not Jan Kowalski here because the suites share one database, and other tests create that user.
	@Test
	void aPersonIsOnePlaceholderToTheModelAndTheirNameToTheUser() {
		var author = "Alice Moreau";
		var customer = login(users.create(Role.USER, author));
		var reference = raiseTicket(customer, "Remove a former employee",
				"Please remove Tadeusz Wrona from our workspace.");
		var support = login(users.create(Role.SUPPORT));
		var written = new AtomicReference<String>();
		model.script(toolCall("getTicket", "{\"reference\":\"" + reference + "\"}"), () -> {
			written.set(ticket().get("summary").get("author").asString() + " wants Tadeusz Wrona removed.");
			return answer(written.get()).respond();
		});

		var reply = ask(support, "Who raised " + reference + ", and whom do they want removed?");

		assertThat(ticket(support, reference).get("summary").get("author").get("name").asString())
				.isEqualTo(author);
		assertThat(ticket().get("summary").get("author").asString()).isEqualTo("<PERSON_1>");
		assertThat(ticket().get("description").asString()).isEqualTo("Please remove Tadeusz Wrona from our workspace.");
		assertThat(written.get()).isEqualTo("<PERSON_1> wants Tadeusz Wrona removed.").doesNotContain(author);
		assertThat(model.prompts().getLast().getInstructions().toString()).doesNotContain(author);
		assertThat(reply.text()).isEqualTo(author + " wants Tadeusz Wrona removed.");
	}

	private void leavePersonalDataToTheGateway() {
		settings.change(new ChatSettingsChange(false, false, true, false, "chat-gemini", null, null));
	}

	private JsonNode ticket() {
		return JSON.readTree(model.toolResults().getFirst());
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
