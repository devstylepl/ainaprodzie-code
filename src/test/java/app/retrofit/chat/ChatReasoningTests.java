package app.retrofit.chat;

import static app.retrofit.chat.StubModelServer.answerStream;
import static app.retrofit.chat.StubModelServer.reasoningStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import app.retrofit.RandomPortContext;
import app.retrofit.TestUsers;
import app.retrofit.auth.Role;
import app.retrofit.chat.ChatServerContext.Reply;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import tools.jackson.databind.json.JsonMapper;

/// The reasoning a model returns before its answer streams as `reasoning` events and is never the answer (chat ADR
/// 0020). The real OpenAI client streams from [StubModelServer], which writes the reasoning as LiteLLM does, in
/// `reasoning_content`: reading that field is the client's work, so a scripted model would prove nothing.
@TestPropertySource(properties = "spring.ai.model.chat=openai")
@Import(StubModelServer.Wiring.class)
@ExtendWith(OutputCaptureExtension.class)
class ChatReasoningTests extends RandomPortContext {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String USAGE = "{\"prompt_tokens\":300,\"completion_tokens\":40,\"total_tokens\":340}";

	@Autowired
	StubModelServer modelServer;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	ChatSettingStore settings;

	@Autowired
	InMemorySpanExporter exported;

	@Autowired
	SdkTracerProvider tracerProvider;

	String session;

	@BeforeEach
	void signIn() {
		var user = new TestUsers(jdbc, passwordEncoder).create(Role.USER);
		session = login(user.email(), user.password());
	}

	/// The suites share one row of settings, so the app traces one test turns on go off again.
	@AfterEach
	void storeNoAppTraces() {
		settings.change(new ChatSettingsChange(false, null, null, null, null, null, null));
	}

	@Test
	void reasoningBeforeTheAnswerStreamsAsReasoningEventsThenTheAnswer() {
		modelServer.script(reasoningStream(List.of("Seventeen times twenty ", "is 340, plus 51."),
				"It is 391.", USAGE));

		var reply = ask(UUID.randomUUID(), "What is 17 times 23?");

		assertThat(reply.names()).containsExactly("reasoning", "reasoning", "token", "done");
		assertThat(reply.events("reasoning")).extracting(event -> event.data().get("text").asString())
				.containsExactly("Seventeen times twenty ", "is 340, plus 51.");
		assertThat(reply.events("reasoning")).allSatisfy(event -> assertThat(
				event.data().get("type").asString()).isEqualTo("reasoning"));
		assertThat(reply.text()).isEqualTo("It is 391.");
	}

	/// The answer's hold-back does not count the reasoning's letters.
	@Test
	void reasoningFollowedByAnAnswerWithoutALetterOrDigitEndsWithFailed() {
		modelServer.script(reasoningStream(List.of("The user greets me."), ":", USAGE));

		var reply = ask(UUID.randomUUID(), "Hello");

		assertThat(reply.names()).containsExactly("reasoning", "error");
		assertThat(reply.events("error").getFirst().data().get("code").asString()).isEqualTo("failed");
	}

	@Test
	void reasoningWithNoAnswerEndsWithFailed() {
		modelServer.script(reasoningStream(List.of("The user greets me."), "", USAGE));

		var reply = ask(UUID.randomUUID(), "Hello");

		assertThat(reply.names()).containsExactly("reasoning", "error");
		assertThat(reply.events("error").getFirst().data().get("code").asString()).isEqualTo("failed");
	}

	/// The conversation does not remember the reasoning (chat ADR 0005).
	@Test
	void theNextModelRequestHoldsTheQuestionAndTheAnswerWithoutTheReasoning() {
		var conversationId = UUID.randomUUID();
		modelServer.script(reasoningStream(List.of("Seventeen times twenty is 340."), "It is 391.", USAGE),
				answerStream("Twice that is 782.", USAGE));
		ask(conversationId, "What is 17 times 23?");

		var reply = ask(conversationId, "And twice that?");

		assertThat(reply.names()).last().isEqualTo("done");
		var next = modelServer.requests().getLast().body();
		var messages = JSON.readTree(next).get("messages");
		assertThat(messages.valueStream().map(message -> message.get("role").asString()))
				.containsExactly("system", "user", "assistant", "user");
		assertThat(messages.get(1).get("content").asString()).isEqualTo("What is 17 times 23?");
		assertThat(messages.get(2).get("content").asString()).isEqualTo("It is 391.");
		assertThat(next).doesNotContain("reasoning_content").doesNotContain("Seventeen times twenty");
	}

	/// The app's spans hold the question and the answer while app traces are on, never the reasoning.
	@Test
	void noSpanAndNoLogLineOfTheAppHoldsTheReasoning(CapturedOutput output) {
		settings.change(new ChatSettingsChange(true, false, true, false, "chat-gemini", null, null));
		tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
		exported.reset();
		modelServer.script(reasoningStream(List.of("Seventeen times twenty is 340."), "It is 391.", USAGE));

		var reply = ask(UUID.randomUUID(), "What is 17 times 23?");

		assertThat(reply.names()).containsExactly("reasoning", "token", "done");
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
			assertThat(exported.getFinishedSpanItems()).anySatisfy(span -> assertThat(
					span.getAttributes().asMap().values()).anySatisfy(value -> assertThat(
							value.toString()).contains("It is 391.")));
		});
		assertThat(exported.getFinishedSpanItems()).allSatisfy(span -> assertThat(
				span.getAttributes().asMap().values()).noneSatisfy(value -> assertThat(
						value.toString()).contains("Seventeen times twenty")));
		assertThat(output.getAll()).doesNotContain("Seventeen times twenty");
	}

	private Reply ask(UUID conversationId, String text) {
		var csrf = primeCsrf();
		var response = http().post().uri("/api/chat/conversations/{conversationId}/messages", conversationId)
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
