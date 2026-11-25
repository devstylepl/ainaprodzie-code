package app.retrofit.chat;

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

/// A request LiteLLM refuses with 400. The guardrail's refusal of a card number (chat ADR 0017) ends the question with
/// `guardrails-blocked`, any other refusal with `failed`. The real OpenAI client reads the refusals from
/// [StubModelServer], because the code is chosen by the message LiteLLM writes into its error.
@TestPropertySource(properties = "spring.ai.model.chat=openai")
@Import(StubModelServer.Wiring.class)
class ChatRefusedRequestTests extends RandomPortContext {

	@Autowired
	StubModelServer modelServer;

	@Autowired
	PasswordEncoder passwordEncoder;

	/// LiteLLM v1.108.3's answer to a request holding a card number, word for word.
	@Test
	void aRequestTheGuardrailBlocksEndsTheQuestionWithGuardrailsBlocked() {
		modelServer.refuse(400, """
				{"error": {"message": "Blocked entity detected: CREDIT_CARD by Guardrail: personal-data. This entity \
				is not allowed to be used in this request.", "type": "invalid_request_error", "param": null, \
				"code": "400"}}""");

		var reply = ask("Card 4111 1111 1111 1111");

		assertThat(reply.names()).containsExactly("error");
		assertThat(reply.events().getFirst().data().get("code").asString()).isEqualTo("guardrails-blocked");
	}

	@Test
	void aRequestRefusedForAnotherReasonEndsTheQuestionWithFailed() {
		modelServer.refuse(400, """
				{"error": {"message": "Invalid model name passed in model=chat-unknown. Call `/v1/models` to view \
				available models for your key.", "type": "invalid_request_error", "param": "model", "code": "400"}}""");

		var reply = ask("Hello");

		assertThat(reply.names()).containsExactly("error");
		assertThat(reply.events().getFirst().data().get("code").asString()).isEqualTo("failed");
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
