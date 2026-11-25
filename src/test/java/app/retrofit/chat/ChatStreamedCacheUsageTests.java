package app.retrofit.chat;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import app.retrofit.RandomPortContext;
import app.retrofit.TestUsers;
import app.retrofit.auth.Role;
import app.retrofit.chat.ChatEvalConfiguration.ModelRequestRecorder;
import app.retrofit.chat.ChatServerContext.Reply;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;

/// The cache usage of a streamed model call reaches its span and the evaluations' handler, although Spring AI drops it
/// when it aggregates a stream (spring-projects/spring-ai#6667). The real OpenAI client streams from a stub server that
/// answers as LiteLLM does: the cache read as `prompt_tokens_details.cached_tokens`, the cache write as
/// `cache_creation_input_tokens`, both in the last chunk's usage.
///
/// A context of its own, because the real model client is the point of the test: the other suites script the model.
@TestPropertySource(properties = "spring.ai.model.chat=openai")
@Import(StubModelServer.Wiring.class)
class ChatStreamedCacheUsageTests extends RandomPortContext {

	private static final String CACHE_READ = "gen_ai.usage.cache_read.input_tokens";

	private static final String CACHE_WRITE = "gen_ai.usage.cache_creation.input_tokens";

	@Autowired
	StubModelServer modelServer;

	@Autowired
	ModelRequestRecorder modelRequests;

	@Autowired
	InMemorySpanExporter exported;

	@Autowired
	SdkTracerProvider tracerProvider;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	ChatSettingStore settings;

	@BeforeEach
	void forgetEarlierCalls() {
		settings.change(new ChatSettingsChange(true, false, true, true, "chat-gemini", null, null));
		tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
		exported.reset();
		modelRequests.reset();
	}

	@Test
	void theModelCallSpanCarriesTheCacheReadAndTheCacheWriteOfAStreamedAnswer() {
		modelServer.answer("You have no new tickets.",
				"{\"prompt_tokens\":1200,\"completion_tokens\":8,\"total_tokens\":1208,"
						+ "\"prompt_tokens_details\":{\"cached_tokens\":1024},\"cache_creation_input_tokens\":150}");

		var reply = ask("Which of our tickets are new?");

		assertThat(reply.names()).last().isEqualTo("done");
		var modelCall = modelCall();
		assertThat(modelCall.getAttributes().get(stringKey(CACHE_READ))).isEqualTo("1024");
		assertThat(modelCall.getAttributes().get(stringKey(CACHE_WRITE))).isEqualTo("150");
	}

	@Test
	void theEvaluationsHandlerReadsTheCacheReadAndTheCacheWriteFromTheResponsesUsage() {
		modelServer.answer("You have no new tickets.",
				"{\"prompt_tokens\":1200,\"completion_tokens\":8,\"total_tokens\":1208,"
						+ "\"prompt_tokens_details\":{\"cached_tokens\":1024},\"cache_creation_input_tokens\":150}");

		var reply = ask("Which of our tickets are new?");

		assertThat(reply.names()).last().isEqualTo("done");
		modelCall();
		var spend = modelRequests.spend(Duration.ZERO);
		assertThat(spend.modelRequests()).isEqualTo(1);
		assertThat(spend.inputTokens()).isEqualTo(1200);
		assertThat(spend.cachedInputTokens()).isEqualTo(1024);
		assertThat(spend.cacheWriteTokens()).isEqualTo(150);
		assertThat(spend.outputTokens()).isEqualTo(8);
	}

	/// What Gemini reports for a prompt it read nothing of from its cache: a cache read of zero is a number to show.
	@Test
	void aCacheReadOfZeroIsOnTheSpanAsZero() {
		modelServer.answer("You have no new tickets.",
				"{\"prompt_tokens\":1200,\"completion_tokens\":8,\"total_tokens\":1208,"
						+ "\"prompt_tokens_details\":{\"cached_tokens\":0}}");

		ask("Which of our tickets are new?");

		var modelCall = modelCall();
		assertThat(modelCall.getAttributes().get(stringKey(CACHE_READ))).isEqualTo("0");
		assertThat(modelCall.getAttributes().get(stringKey(CACHE_WRITE))).isNull();
	}

	@Test
	void aStreamWithoutCacheUsageSetsNeitherAttribute() {
		modelServer.answer("You have no new tickets.",
				"{\"prompt_tokens\":1200,\"completion_tokens\":8,\"total_tokens\":1208}");

		var reply = ask("Which of our tickets are new?");

		assertThat(reply.names()).last().isEqualTo("done");
		var modelCall = modelCall();
		assertThat(modelCall.getAttributes().get(stringKey("gen_ai.usage.input_tokens"))).isEqualTo("1200");
		assertThat(modelCall.getAttributes().get(stringKey(CACHE_READ))).isNull();
		assertThat(modelCall.getAttributes().get(stringKey(CACHE_WRITE))).isNull();
		var spend = modelRequests.spend(Duration.ZERO);
		assertThat(spend.cachedInputTokens()).isZero();
		assertThat(spend.cacheWriteTokens()).isZero();
	}

	/// LiteLLM's span of the call counts its tokens and cost, so with the switch off the app's model span counts nothing
	/// a second time: no token counts, cache ones included, and no model name Phoenix could price (chat ADR 0016).
	@Test
	void withAppLlmSpansOffTheModelCallSpanIsAStepWithoutTokensOrModel() {
		settings.change(new ChatSettingsChange(true, false, false, true, "chat-gemini", null, null));
		modelServer.answer("Hello! How can I help?",
				"{\"prompt_tokens\":1200,\"completion_tokens\":8,\"total_tokens\":1208,"
						+ "\"prompt_tokens_details\":{\"cached_tokens\":1024},\"cache_creation_input_tokens\":150}");

		var reply = ask("Hello");

		assertThat(reply.names()).last().isEqualTo("done");
		var attributes = modelCall().getAttributes().asMap().keySet().stream().map(key -> key.getKey()).toList();
		assertThat(attributes).noneMatch(key -> key.startsWith("gen_ai.usage."))
				.doesNotContain("gen_ai.request.model", "gen_ai.response.model")
				.contains("gen_ai.input.messages", "gen_ai.output.messages", "session.id");
		assertThat(modelCall().getAttributes().get(stringKey("openinference.span.kind"))).isEqualTo("CHAIN");
	}

	/// On, the app's model span is an LLM span with the call's tokens and model, beside LiteLLM's.
	@Test
	void withAppLlmSpansOnTheModelCallSpanIsAnLlmSpanWithTokensAndModel() {
		modelServer.answer("Hello! How can I help?",
				"{\"prompt_tokens\":1200,\"completion_tokens\":8,\"total_tokens\":1208}");

		ask("Hello");

		var modelCall = modelCall();
		assertThat(modelCall.getAttributes().get(stringKey("openinference.span.kind"))).isEqualTo("LLM");
		assertThat(modelCall.getAttributes().get(stringKey("gen_ai.usage.total_tokens"))).isEqualTo("1208");
		assertThat(modelCall.getAttributes().get(stringKey("gen_ai.request.model"))).isEqualTo("chat-gemini");
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

	/// The one model call span of the question, once exported.
	private SpanData modelCall() {
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
			assertThat(modelCalls()).hasSize(1);
		});
		return modelCalls().getFirst();
	}

	private List<SpanData> modelCalls() {
		return exported.getFinishedSpanItems().stream()
				.filter(span -> "chat".equals(span.getAttributes().get(stringKey("gen_ai.operation.name"))))
				.toList();
	}

	/// Registered as the evaluations register theirs, so it sees what their report would.
	@TestConfiguration(proxyBeanMethods = false)
	static class ModelRequestRecorderConfiguration {

		@Bean
		ModelRequestRecorder modelRequestRecorder() {
			return new ModelRequestRecorder();
		}

	}

}
