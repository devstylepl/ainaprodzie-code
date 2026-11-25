package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import app.retrofit.RandomPortContext;
import app.retrofit.TestUsers;
import app.retrofit.auth.Role;

/// The evaluations learn which provider model answered for an alias from LiteLLM's response (chat ADR 0003). The real
/// OpenAI client streams from [StubModelServer], which names a model in `x-litellm-model-name` as LiteLLM does, and
/// the question is asked through [ChatService], as an evaluation asks it.
@TestPropertySource(properties = "spring.ai.model.chat=openai")
@Import({ StubModelServer.Wiring.class, AnsweringModelRecorder.Wiring.class })
class AnsweringModelRecorderTests extends RandomPortContext {

	private static final String USAGE = "{\"prompt_tokens\":1200,\"completion_tokens\":8,\"total_tokens\":1208}";

	@Autowired
	StubModelServer modelServer;

	@Autowired
	AnsweringModelRecorder recorder;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	ChatSettingStore settings;

	@Autowired
	ChatService chat;

	@BeforeEach
	void askChatGeminiAndForgetEarlierAnswers() {
		settings.change(new ChatSettingsChange(false, false, true, true, "chat-gemini", null, null));
		recorder.take();
	}

	/// The alias is the request's and the model the response's: once a fallback answered, the response no longer
	/// names the alias that was asked.
	@Test
	void anAnswerIsRecordedUnderTheAliasItsRequestNamed() {
		modelServer.answer("You have no new tickets.", USAGE);
		modelServer.answeredBy("anthropic/claude-haiku-5-5");

		assertThat(ask()).last().isInstanceOf(ChatEvent.Done.class);

		assertThat(recorder.take()).containsExactly(entry("chat-gemini", List.of("anthropic/claude-haiku-5-5")));
		assertThat(recorder.take()).isEmpty();
	}

	/// Only answers count: a refusal names the model that refused, and the SDK retries it.
	@Test
	void aRefusedRequestThatCarriesTheHeaderRecordsNothing() {
		modelServer.refuse(429, """
				{"error": {"message": "litellm.RateLimitError: Rate limit reached", "type": "throttling_error", \
				"param": null, "code": "429"}}""");
		modelServer.answeredBy("anthropic/claude-haiku-5-5");

		assertThat(ask()).last().isEqualTo(new ChatEvent.Error(ChatErrorCode.RATE_LIMITED));

		assertThat(recorder.take()).isEmpty();
	}

	private List<ChatEvent> ask() {
		var customer = new TestUsers(jdbc, passwordEncoder).create(Role.USER);
		return chat.ask(new ChatService.Question(customer.id(), UUID.randomUUID(), InterfaceLanguage.EN,
				"Which of our tickets are new?", null)).collectList().block(Duration.ofSeconds(10));
	}

}
