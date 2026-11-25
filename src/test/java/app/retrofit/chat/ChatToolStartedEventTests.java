package app.retrofit.chat;

import static app.retrofit.chat.StubModelServer.answerStream;
import static app.retrofit.chat.StubModelServer.toolCallsWrittenInParts;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import app.retrofit.RandomPortContext;
import app.retrofit.TestUsers;
import app.retrofit.auth.Role;
import app.retrofit.chat.ChatServerContext.Reply;

/// A tool call the model is still writing is a `toolStarted` event before its `tool` event (chat ADR 0004). The real
/// OpenAI client reads the calls from [StubModelServer], because the event comes from the parts the client makes of a
/// provider's argument deltas.
@TestPropertySource(properties = "spring.ai.model.chat=openai")
@Import(StubModelServer.Wiring.class)
class ChatToolStartedEventTests extends RandomPortContext {

	private static final String USAGE = "{\"prompt_tokens\":10,\"completion_tokens\":2,\"total_tokens\":12}";

	@Autowired
	StubModelServer modelServer;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void aToolCallTheModelIsStillWritingIsAToolStartedEventBeforeItsToolEvent() {
		modelServer.script(toolCallsWrittenInParts("getTicket", "{\"reference\":\"RET-1\"}"),
				answerStream("There is no ticket RET-1.", USAGE));

		var reply = ask("What is RET-1 about?");

		assertThat(reply.names()).startsWith("toolStarted", "tool", "token").endsWith("done");
		assertThat(reply.events("toolStarted")).singleElement()
				.extracting(ChatServerContext.Event::data)
				.isEqualTo(ChatServerContext.JSON
						.readTree("{\"type\":\"toolStarted\",\"tool\":\"getTicket\",\"toolCall\":0}"));
		assertThat(reply.events("tool").getFirst().data().get("arguments").get("reference").asString())
				.isEqualTo("RET-1");
	}

	@Test
	void aStartedCallHasTheNumberItsToolEventWillHave() {
		modelServer.script(
				toolCallsWrittenInParts("getTicket", "{\"reference\":\"RET-1\"}", "{\"reference\":\"RET-2\"}"),
				toolCallsWrittenInParts("getTicket", "{\"reference\":\"RET-3\"}"),
				answerStream("There are no such tickets.", USAGE));

		var reply = ask("What are RET-1, RET-2 and RET-3 about?");

		assertThat(reply.names())
				.startsWith("toolStarted", "toolStarted", "tool", "tool", "toolStarted", "tool", "token");
		assertThat(reply.events("toolStarted")).extracting(event -> event.data().get("toolCall").asInt())
				.containsExactly(0, 1, 2);
		assertThat(reply.events("tool")).extracting(event -> event.data().get("arguments").get("reference").asString())
				.containsExactly("RET-1", "RET-2", "RET-3");
	}

	@Test
	void aCallCutOffWhileTheModelWritesItHasNoToolEventAndEndsTheQuestionWithAnError() {
		modelServer.cutOffWhileWriting("getTicket");

		var reply = ask("What is RET-1 about?");

		assertThat(reply.names()).containsExactly("toolStarted", "error");
	}

	private Reply ask(String text) {
		var user = new TestUsers(jdbc, passwordEncoder).create(Role.USER);
		var session = login(user.email(), user.password());
		var csrf = primeCsrf();
		var response = http().post().uri("/api/chat/conversations/{conversationId}/messages", UUID.randomUUID())
				.header("X-XSRF-TOKEN", csrf.header())
				.header(HttpHeaders.COOKIE, csrf.cookie() + "; SESSION=" + session)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("text", text))
				.retrieve()
				.toEntity(String.class);
		return new Reply(response.getStatusCode(), response.getHeaders().getContentType(),
				response.getBody() == null ? "" : response.getBody());
	}

}
