package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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

/// `spring.ai.openai.timeout` bounds one model call: a model that has not answered by then ends the question with
/// `timeout`, well before the turn's deadline (3s in the tests). Spring AI 2.0.1 ignored the setting and held every
/// call for 60s (spring-projects/spring-ai#6837). No retry, so the question waits for one call only.
@TestPropertySource(properties = { "spring.ai.model.chat=openai", "spring.ai.openai.timeout=500ms",
		"spring.ai.openai.max-retries=0" })
@Import(StubModelServer.Wiring.class)
class ChatModelTimeoutTests extends RandomPortContext {

	@Autowired
	StubModelServer modelServer;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void aModelCallPastTheClientsTimeoutEndsTheQuestionWithTimeout() {
		modelServer.answer("You have no new tickets.",
				"{\"prompt_tokens\":1200,\"completion_tokens\":8,\"total_tokens\":1208}", Duration.ofSeconds(2));

		var started = System.nanoTime();
		var reply = ask("Which of our tickets are new?");
		var took = Duration.ofNanos(System.nanoTime() - started);

		assertThat(reply.names()).containsExactly("error");
		assertThat(reply.events().getFirst().data().get("code").asString()).isEqualTo("timeout");
		assertThat(took).isLessThan(Duration.ofSeconds(2));
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
