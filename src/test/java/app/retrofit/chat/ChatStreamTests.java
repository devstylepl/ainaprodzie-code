package app.retrofit.chat;

import static app.retrofit.chat.ScriptedChatModel.after;
import static app.retrofit.chat.ScriptedChatModel.answer;
import static app.retrofit.chat.ScriptedChatModel.failure;
import static app.retrofit.chat.ScriptedChatModel.toolCall;
import static app.retrofit.chat.ScriptedChatModel.untilCancelled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import com.openai.models.ErrorObject;

import app.retrofit.auth.Role;
import tools.jackson.databind.JsonNode;

/// `POST /api/chat/conversations/{conversationId}/messages`: the answer as a stream of typed events (chat ADR 0004),
/// its failures, and what Spring Security does on the async dispatch that completes the stream.
@ExtendWith(OutputCaptureExtension.class)
class ChatStreamTests extends ChatServerContext {

	/// Words of the user's that a failure's message could quote.
	private static final String MARKER = "MARKER_jan_kowalski_512345678";

	@Test
	void aToolCallStreamsAsAToolEventThenTheAnswerTokensThenDone() {
		var customer = users.create(Role.USER);
		var session = login(customer);
		model.script(toolCall("searchTickets", "{\"statuses\":[\"NEW\"],\"limit\":5}"),
				answer("You have no new tickets."));

		var reply = ask(session, "Which of our tickets are new?");

		assertThat(reply.status()).isEqualTo(HttpStatus.OK);
		assertThat(reply.contentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
		assertThat(reply.names()).first().isEqualTo("tool");
		assertThat(reply.names()).last().isEqualTo("done");
		assertThat(reply.names().subList(1, reply.names().size() - 1)).hasSizeGreaterThan(1).containsOnly("token");
		var tool = reply.events().getFirst().data();
		assertThat(tool.get("type").asString()).isEqualTo("tool");
		assertThat(tool.get("tool").asString()).isEqualTo("searchTickets");
		assertThat(tool.get("arguments").get("limit").asInt()).isEqualTo(5);
		assertThat(tool.get("arguments").get("statuses").get(0).asString()).isEqualTo("NEW");
		assertThat(tool.toString()).doesNotContain(customer.id().toString());
		assertThat(reply.events().getLast().data().get("type").asString()).isEqualTo("done");
		assertThat(reply.text()).isEqualTo("You have no new tickets.");
	}

	@Test
	void aRateLimitedModelEndsTheStreamWithRateLimited(CapturedOutput output) {
		var session = login(users.create(Role.USER));
		model.script(failure(new CompletionException(
				RateLimitException.builder().headers(Headers.builder().build()).build())));

		var reply = ask(session, "Which tickets are new?");

		assertThat(reply.status()).isEqualTo(HttpStatus.OK);
		assertThat(reply.names()).containsExactly("error");
		assertThat(reply.events().getFirst().data().get("code").asString()).isEqualTo("rate-limited");
		assertThat(occurrences(output, "Chat answer failed")).isEqualTo(1);
		assertThat(output.getOut()).doesNotContain("Which tickets are new?");
	}

	/// LiteLLM's error body can echo the request, so the user's words must not reach the log through the message of
	/// the client's exception.
	@Test
	void aFailureWhoseMessageEchoesTheQuestionIsLoggedWithoutIt(CapturedOutput output) {
		var session = login(users.create(Role.USER));
		model.script(failure(new CompletionException(BadRequestException.builder()
				.headers(Headers.builder().build())
				.error(ErrorObject.builder().message("Invalid request: " + MARKER).type("invalid_request_error")
						.code("400").param("messages").build())
				.build())));

		var reply = ask(session, "Which tickets are new?");

		assertThat(reply.events().getFirst().data().get("code").asString()).isEqualTo("failed");
		assertThat(occurrences(output, "Chat answer failed")).isEqualTo(1);
		assertThat(output.getOut()).contains(BadRequestException.class.getName()).doesNotContain(MARKER);
	}

	/// A binding failure quotes the model's arguments, which can carry the user's words: the log names the tool and
	/// the cause's class, never its message.
	@Test
	void aToolArgumentThatCannotBeBoundIsLoggedWithoutTheArguments(CapturedOutput output) {
		var session = login(users.create(Role.USER));
		model.script(toolCall("searchTickets", "{\"statuses\":[\"" + MARKER + "\"]}"),
				answer("I could not read the tickets right now."));

		var reply = ask(session, "Which tickets are new?");

		assertThat(reply.names()).last().isEqualTo("done");
		assertThat(model.toolResults()).containsExactly(ChatConfiguration.TOOL_FAILURE);
		assertThat(occurrences(output, "Tool searchTickets failed")).isEqualTo(1);
		assertThat(output.getOut()).doesNotContain(MARKER);
	}

	@Test
	void aServerErrorOrAnUnreachableProxyEndsTheStreamWithUnavailable() {
		var session = login(users.create(Role.USER));

		model.script(failure(InternalServerException.builder().statusCode(500).headers(Headers.builder().build())
				.build()));
		var serverError = ask(session, "Which tickets are new?");
		model.script(failure(new OpenAIIoException("Request failed", new ConnectException("Connection refused"))));
		var unreachable = ask(session, "Which tickets are new?");

		assertThat(serverError.events().getLast().data().get("code").asString()).isEqualTo("unavailable");
		assertThat(unreachable.events().getLast().data().get("code").asString()).isEqualTo("unavailable");
	}

	@Test
	void aTimeoutEndsTheStreamWithTimeout() {
		var session = login(users.create(Role.USER));
		model.script(failure(new OpenAIIoException("Request failed", new SocketTimeoutException("timeout"))));

		var reply = ask(session, "Which tickets are new?");

		assertThat(reply.names()).containsExactly("error");
		assertThat(reply.events().getFirst().data().get("code").asString()).isEqualTo("timeout");
	}

	/// The spike saw a model answer `:` and nothing else: an answer without a letter or digit counts as empty.
	@ParameterizedTest
	@ValueSource(strings = { "", ":", " . " })
	void anAnswerWithoutALetterOrDigitEndsTheStreamWithFailed(String text) {
		var session = login(users.create(Role.USER));
		model.script(answer(text));

		var reply = ask(session, "Which tickets are new?");

		assertThat(reply.names()).containsExactly("error");
		assertThat(reply.events().getFirst().data().get("code").asString()).isEqualTo("failed");
	}

	@Test
	void punctuationBeforeTheFirstWordReachesTheUserWithIt() {
		var session = login(users.create(Role.USER));
		model.script(answer(". No tickets."));

		var reply = ask(session, "Which tickets are new?");

		assertThat(reply.names()).last().isEqualTo("done");
		assertThat(reply.text()).isEqualTo(". No tickets.");
	}

	@Test
	void theSeventhToolCallOfAQuestionEndsTheStreamWithFailed() {
		var session = login(users.create(Role.USER));
		model.script(toolCall("searchTickets", "{}"), toolCall("searchTickets", "{}"), toolCall("searchTickets", "{}"),
				toolCall("searchTickets", "{}"), toolCall("searchTickets", "{}"), toolCall("searchTickets", "{}"),
				toolCall("searchTickets", "{}"), answer("Never reached."));

		var reply = ask(session, "How long has our oldest ticket been waiting?");

		assertThat(reply.names()).containsExactlyElementsOf(concat(Collections.nCopies(7, "tool"), "error"));
		assertThat(reply.events().getLast().data().get("code").asString()).isEqualTo("failed");
		assertThat(reply.text()).isEmpty();
	}

	@Test
	void aBlankOrTooLongQuestionIsAValidationProblemBeforeAnyStream() {
		var session = login(users.create(Role.USER));

		var blank = ask(session, "   ");
		var tooLong = ask(session, "x".repeat(ChatController.TEXT_MAX + 1));
		model.script(answer("Fine."));
		var longest = ask(session, "x".repeat(ChatController.TEXT_MAX));

		assertThat(blank.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(blank.problem().get("errors").get(0).get("code").asString()).isEqualTo("field.required");
		assertThat(tooLong.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(tooLong.problem().get("type").asString()).isEqualTo("https://retrofit.app/problems/validation");
		assertThat(tooLong.problem().get("errors").get(0).get("code").asString()).isEqualTo("field.too-long");
		assertThat(longest.names()).last().isEqualTo("done");
	}

	@Test
	void aConversationIdThatIsNotAUuidIsAMalformedRequest() {
		var session = login(users.create(Role.USER));
		var csrf = primeCsrf();

		var response = http().post().uri("/api/chat/conversations/{conversationId}/messages", "not-a-uuid")
				.header("X-XSRF-TOKEN", csrf.header())
				.header(HttpHeaders.COOKIE, csrf.cookie() + "; SESSION=" + session)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("text", "Hello"))
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("https://retrofit.app/problems/malformed-request");
	}

	/// The question is one operation (chat ADR 0002): a caller disabled after it arrived finishes it, its tools still
	/// reading as them, and is refused the next one. The async dispatch that completes the stream answers no 401 or 403
	/// on the committed response.
	@Test
	void aCallerDisabledMidQuestionFinishesItAndIsRefusedTheNext(CapturedOutput output) {
		var customer = users.create(Role.USER);
		var session = login(customer);
		var reference = raiseTicket(session, "Export to PDF fails");
		model.script(after(() -> users.disable(customer.id()), toolCall("searchTickets", "{}")),
				answer("You have one ticket."));

		var reply = ask(session, "Which tickets do we have?");

		assertThat(reply.status()).isEqualTo(HttpStatus.OK);
		assertThat(reply.names()).first().isEqualTo("tool");
		assertThat(reply.names()).last().isEqualTo("done");
		assertThat(model.toolResults()).singleElement().asString().contains(reference);
		assertThat(output.getOut()).doesNotContain("Tool searchTickets failed", "Request refused: POST /api/chat");
		assertThat(ask(session, "And now?").status()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	/// The session ends in another tab while the answer streams: the stream still completes, its async dispatch is
	/// not refused on the committed response, and the ended session stays ended.
	@Test
	void aSessionEndedMidStreamDoesNotRefuseTheCommittedStream(CapturedOutput output) {
		var session = login(users.create(Role.USER));
		model.script(after(() -> logout(session), answer("You have no tickets.")));

		var reply = ask(session, "Which tickets do we have?");

		assertThat(reply.status()).isEqualTo(HttpStatus.OK);
		assertThat(reply.names()).last().isEqualTo("done");
		assertThat(reply.body()).doesNotContain("unauthenticated");
		assertThat(output.getOut()).doesNotContain("Request refused: POST /api/chat");
		assertThat(ask(session, "And now?").status()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void conversationsOfTwoUsersWithTheSameIdAreSeparate() {
		var alice = login(users.create(Role.USER));
		var bob = login(users.create(Role.USER));
		var conversation = UUID.randomUUID();
		model.script(answer("Alice's answer."));
		ask(alice, conversation, "Alice's question");

		model.script(answer("Bob's answer."));
		ask(bob, conversation, "Bob's question");
		var bobsPrompt = model.prompts().getFirst().getContents();
		model.script(answer("Again."));
		ask(alice, conversation, "Alice's follow-up");
		var alicesPrompt = model.prompts().getFirst().getContents();

		assertThat(bobsPrompt).doesNotContain("Alice's question", "Alice's answer.");
		assertThat(alicesPrompt).contains("Alice's question", "Alice's answer.").doesNotContain("Bob's");
	}

	@Test
	void forgettingAConversationStartsItAfresh() {
		var session = login(users.create(Role.USER));
		var conversation = UUID.randomUUID();
		model.script(answer("First answer."));
		ask(session, conversation, "First question");

		var forgotten = forget(session, conversation);
		model.script(answer("Second answer."));
		ask(session, conversation, "Second question");

		assertThat(forgotten).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(model.prompts().getFirst().getContents()).doesNotContain("First question", "First answer.");
	}

	@Test
	void aFailedTurnLeavesTheConversationAsItWas() {
		var session = login(users.create(Role.USER));
		var conversation = UUID.randomUUID();
		model.script(answer("First answer."));
		ask(session, conversation, "First question");
		model.script(toolCall("searchTickets", "{}"), failure(new OpenAIIoException("Request failed",
				new SocketTimeoutException("timeout"))));
		ask(session, conversation, "Failed question");

		model.script(answer("Third answer."));
		ask(session, conversation, "Third question");

		assertThat(model.prompts().getFirst().getContents()).contains("First question", "First answer.")
				.doesNotContain("Failed question");
	}

	/// The user stops the answer, or closes the tab: the model call is cancelled, and the conversation remembers
	/// nothing of the turn.
	@Test
	void closingTheRequestCancelsTheModelCallAndStoresNoTurn() throws Exception {
		var session = login(users.create(Role.USER));
		var conversation = UUID.randomUUID();
		model.script(answer("First answer."));
		ask(session, conversation, "First question");
		var cancelled = new CountDownLatch(1);
		model.script(untilCancelled("Streaming ", cancelled));

		try (var stream = new OpenStream(port, session, primeCsrf(), conversation, "Stopped question")) {
			assertThat(stream.status()).startsWith("HTTP/1.1 200");
			assertThat(stream.nextEvent()).isEqualTo("token");
		}

		assertThat(cancelled.await(10, TimeUnit.SECONDS)).as("the model call is cancelled").isTrue();
		model.script(answer("Third answer."));
		ask(session, conversation, "Third question");
		assertThat(model.prompts().getFirst().getContents()).contains("First question", "First answer.")
				.doesNotContain("Stopped question", "Streaming");
	}

	/// A turn has one deadline however steadily the model streams: the stream ends with `timeout`, the model call is
	/// cancelled, and the conversation remembers nothing of the turn.
	@Test
	void aTurnPastItsTimeLimitEndsWithTimeoutAndStoresNoTurn() throws Exception {
		var session = login(users.create(Role.USER));
		var conversation = UUID.randomUUID();
		model.script(answer("First answer."));
		ask(session, conversation, "First question");
		var cancelled = new CountDownLatch(1);
		model.script(untilCancelled("Streaming ", cancelled));

		try (var stream = new OpenStream(port, session, primeCsrf(), conversation, "Endless question")) {
			assertThat(stream.status()).startsWith("HTTP/1.1 200");
			assertThat(stream.nextEvent()).as("the answer streams before the deadline").isEqualTo("token");
			var error = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> stream.nextData("error"));
			assertThat(error.get("code").asString()).isEqualTo("timeout");
		}

		assertThat(cancelled.await(10, TimeUnit.SECONDS)).as("the model call is cancelled").isTrue();
		model.script(answer("Next answer."));
		ask(session, conversation, "Next question");
		assertThat(model.prompts().getFirst().getContents()).contains("First question", "First answer.")
				.doesNotContain("Endless question", "Streaming");
	}

	/// Everything before the latest message is the same for every caller and every turn (chat ADR 0008): the system
	/// prompt word for word, the tools sorted by name, and the earlier turns without their context blocks.
	@Test
	void theContextBlockIsOnlyInTheLatestMessageAndTheSystemPromptIsTheSameForEveryone() {
		var customer = login(users.create(Role.USER));
		var support = login(users.create(Role.SUPPORT));
		var conversation = UUID.randomUUID();
		model.script(answer("First answer."));
		ask(customer, conversation, "First question");
		var customerPrompt = model.prompts().getFirst();
		model.script(answer("Support answer."));
		ask(support, "Support question");
		var supportPrompt = model.prompts().getFirst();
		model.script(answer("Second answer."));
		ask(customer, conversation, "Second question");
		var followUp = model.prompts().getFirst();

		assertThat(customerPrompt.getSystemMessage().getText()).isEqualTo(supportPrompt.getSystemMessage().getText())
				.startsWith("You are the support assistant of Retrofit Support");
		assertThat(customerPrompt.getUserMessage().getText())
				.matches(Pattern.compile("""
						<context>
						Signed in: a customer of the organization Test Organization [0-9a-f]+
						Current time: \\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ
						Interface language: pl
						</context>
						First question""", Pattern.DOTALL));
		assertThat(supportPrompt.getUserMessage().getText()).contains("Signed in: support staff in the Test Team ");
		var messages = followUp.getInstructions();
		assertThat(messages.get(1).getText()).isEqualTo("First question");
		assertThat(messages.get(2).getText()).isEqualTo("First answer.");
		assertThat(messages.getLast().getText()).startsWith("<context>").endsWith("Second question");
		assertThat(messages).filteredOn(message -> message instanceof UserMessage user && user.getText().contains("<context>"))
				.hasSize(1);
		assertThat(((ToolCallingChatOptions) followUp.getOptions()).getToolCallbacks()
				.stream().map(tool -> tool.getToolDefinition().name()))
				.containsExactly("getTicket", "searchTickets");
	}

	/// The ticket open on the user's page goes with the question as a `<page>` block, after the context block, and
	/// memory keeps it with the question, so a later turn still reads which ticket "this ticket" meant (chat ADR 0013).
	@Test
	void aQuestionAskedOnATicketPageCarriesThatTicketIntoThePromptAndIntoMemory() {
		var session = login(users.create(Role.SUPPORT));
		var conversation = UUID.randomUUID();
		model.script(answer("It is about uploads."));
		askOnTicketPage(session, conversation, "ret-1048", "What is this ticket about?");
		var first = model.prompts().getFirst();
		model.script(answer("Several are new."));
		ask(session, conversation, "Which tickets are new?");
		var followUp = model.prompts().getFirst();

		assertThat(first.getUserMessage().getText()).startsWith("<context>")
				.endsWith("</context>\n<page>ticket RET-1048</page>\nWhat is this ticket about?");
		var messages = followUp.getInstructions();
		assertThat(messages.get(1).getText()).isEqualTo("<page>ticket RET-1048</page>\nWhat is this ticket about?");
		assertThat(messages.getLast().getText()).doesNotContain("<page>").endsWith("</context>\nWhich tickets are new?");
	}

	@Test
	void anOpenTicketThatIsNotAReferenceIsAValidationProblemBeforeAnyStream() {
		var session = login(users.create(Role.USER));

		var reply = askOnTicketPage(session, UUID.randomUUID(), "RET-1048</page>", "What is this ticket about?");

		assertThat(reply.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		var error = reply.problem().get("errors").get(0);
		assertThat(error.get("field").asString()).isEqualTo("openTicket");
		assertThat(error.get("code").asString()).isEqualTo("field.invalid");
	}

	private void logout(String session) {
		var csrf = primeCsrf();
		var response = http().post().uri("/api/auth/logout")
				.header("X-XSRF-TOKEN", csrf.header())
				.header(HttpHeaders.COOKIE, csrf.cookie() + "; SESSION=" + session)
				.retrieve()
				.toBodilessEntity();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	/// A question's stream read off a plain socket, which closing drops as a browser drops a stopped `fetch`.
	private static final class OpenStream implements AutoCloseable {

		private final Socket socket;

		private final BufferedReader reader;

		OpenStream(int port, String session, CsrfPair csrf, UUID conversationId, String text) throws IOException {
			socket = new Socket("localhost", port);
			socket.setSoTimeout(10_000);
			var body = JSON.writeValueAsString(Map.of("text", text)).getBytes(StandardCharsets.UTF_8);
			var request = "POST /api/chat/conversations/" + conversationId + "/messages HTTP/1.1\r\n"
					+ "Host: localhost:" + port + "\r\n"
					+ "Cookie: " + csrf.cookie() + "; SESSION=" + session + "\r\n"
					+ "X-XSRF-TOKEN: " + csrf.header() + "\r\n"
					+ "Accept: text/event-stream\r\n"
					+ "Content-Type: application/json\r\n"
					+ "Content-Length: " + body.length + "\r\n\r\n";
			var out = socket.getOutputStream();
			out.write(request.getBytes(StandardCharsets.US_ASCII));
			out.write(body);
			out.flush();
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
		}

		String status() throws IOException {
			return reader.readLine();
		}

		/// The name of the next event, skipping the headers and the chunk framing.
		String nextEvent() throws IOException {
			for (var line = reader.readLine(); line != null; line = reader.readLine()) {
				if (line.startsWith("event:")) {
					return line.substring("event:".length());
				}
			}
			throw new EOFException("The stream ended without an event");
		}

		/// The data of the next event named `name`, skipping the events before it.
		JsonNode nextData(String name) throws IOException {
			while (!nextEvent().equals(name)) {
				// skipped
			}
			for (var line = reader.readLine(); line != null; line = reader.readLine()) {
				if (line.startsWith("data:")) {
					return JSON.readTree(line.substring("data:".length()));
				}
			}
			throw new EOFException("The event " + name + " has no data");
		}

		@Override
		public void close() throws IOException {
			socket.close();
		}

	}

	private static long occurrences(CapturedOutput output, String text) {
		return output.getOut().lines().filter(line -> line.contains(text)).count();
	}

	private static List<String> concat(List<String> first, String last) {
		var all = new ArrayList<>(first);
		all.add(last);
		return all;
	}

}
