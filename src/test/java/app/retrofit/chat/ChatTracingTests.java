package app.retrofit.chat;

import static app.retrofit.chat.ScriptedChatModel.answer;
import static app.retrofit.chat.ScriptedChatModel.toolCall;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import app.retrofit.auth.Role;
import app.retrofit.chat.ChatTracingTestConfiguration.StubPersonalDataMasker;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import tools.jackson.databind.JsonNode;

/// The trace of a question as it leaves the app (chat ADR 0007): one trace with the HTTP request at its root, a span for
/// each model call and each tool call carrying the prompt's version and the texts, and nothing of other requests.
class ChatTracingTests extends ChatServerContext {

	private static final String QUESTION = "Which of our tickets are new?";

	private static final String ANSWER = "You have no new tickets.";

	@Autowired
	InMemorySpanExporter exported;

	@Autowired
	SdkTracerProvider tracerProvider;

	@Autowired
	StubPersonalDataMasker masker;

	@Autowired
	SystemPrompt systemPrompt;

	@Autowired
	ChatSettingStore settings;

	@BeforeEach
	void forgetEarlierSpans() {
		settings.change(new ChatSettingsChange(true, false, true, true, "chat-gemini", null, null));
		tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
		exported.reset();
	}

	@AfterEach
	void restoreTheMasker() {
		masker.failing(false);
		masker.masking(null);
	}

	@Test
	void aQuestionWithOneToolCallIsOneTraceWhoseModelAndToolCallSpansCarryThePromptVersionAndTheTexts() {
		var customer = users.create(Role.USER);
		var session = login(customer);
		var conversationId = UUID.randomUUID();
		model.script(toolCall("searchTickets", "{\"statuses\":[\"NEW\"]}"), answer(ANSWER));

		var reply = ask(session, conversationId, QUESTION);

		assertThat(reply.names()).last().isEqualTo("done");
		var spans = questionTrace();
		var modelCalls = spans.stream().filter(span -> "chat".equals(operation(span))).toList();
		var toolCalls = spans.stream().filter(span -> "execute_tool".equals(operation(span))).toList();
		assertThat(modelCalls).hasSize(2);
		assertThat(toolCalls).hasSize(1);
		assertThat(spans).extracting(SpanData::getTraceId).containsOnly(httpRequest(spans).getTraceId());
		assertThat(httpRequest(spans).getResource().getAttribute(stringKey("openinference.project.name")))
				.isEqualTo("retrofit");
		assertThat(spans).allSatisfy(span -> assertThat(span == httpRequest(spans)
				|| span.getAttributes().get(stringKey("spring.ai.kind")) != null
				|| operation(span) != null).as("%s is the request or a Spring AI span", span.getName()).isTrue());
		assertThat(text(httpRequest(spans), "openinference.span.kind")).isEqualTo("CHAIN");
		assertThat(modelCalls).allSatisfy(span -> assertThat(text(span, "openinference.span.kind")).isEqualTo("LLM"));
		assertThat(toolCalls).allSatisfy(span -> assertThat(text(span, "openinference.span.kind")).isEqualTo("TOOL"));
		for (var span : concat(modelCalls, toolCalls)) {
			assertThat(text(span, "retrofit.prompt.name")).isEqualTo("chat-system");
			assertThat(text(span, "retrofit.prompt.version")).isEqualTo(systemPrompt.version()).hasSize(12);
			assertThat(text(span, "session.id")).isEqualTo(conversationId.toString());
			assertThat(text(span, "user.id")).isEqualTo(customer.id().toString());
		}

		var toolCallRequest = modelCalls.stream().filter(span -> text(span, "gen_ai.output.messages")
				.contains("tool_call")).findFirst().orElseThrow();
		var input = json(toolCallRequest, "gen_ai.input.messages");
		assertThat(input.get(0).get("role").asString()).isEqualTo("system");
		assertThat(input.get(0).get("parts").get(0).get("content").asString()).isEqualTo(systemPrompt.text());
		assertThat(input.get(1).get("role").asString()).isEqualTo("user");
		assertThat(input.get(1).get("parts").get(0).get("content").asString()).endsWith(QUESTION);
		var toolCallPart = json(toolCallRequest, "gen_ai.output.messages").get(0).get("parts").get(0);
		assertThat(toolCallPart.get("type").asString()).isEqualTo("tool_call");
		assertThat(toolCallPart.get("name").asString()).isEqualTo("searchTickets");
		assertThat(toolCallPart.get("arguments").get("statuses").get(0).asString()).isEqualTo("NEW");

		var answerCall = modelCalls.stream().filter(span -> span != toolCallRequest).findFirst().orElseThrow();
		var answerInput = json(answerCall, "gen_ai.input.messages");
		assertThat(answerInput.get(answerInput.size() - 1).get("role").asString()).isEqualTo("tool");
		assertThat(answerInput.get(answerInput.size() - 1).get("parts").get(0).get("type").asString())
				.isEqualTo("tool_call_response");
		var answerMessage = json(answerCall, "gen_ai.output.messages").get(0);
		assertThat(answerMessage.get("role").asString()).isEqualTo("assistant");
		assertThat(answerMessage.get("parts").get(0).get("content").asString()).isEqualTo(ANSWER);
		assertThat(answerMessage.has("finish_reason")).isTrue();

		var toolCall = toolCalls.getFirst();
		assertThat(text(toolCall, "gen_ai.tool.name")).isEqualTo("searchTickets");
		assertThat(text(toolCall, "gen_ai.tool.call.arguments")).isEqualTo("{\"statuses\":[\"NEW\"]}");
		assertThat(JSON.readTree(text(toolCall, "gen_ai.tool.call.result")).has("tickets")).isTrue();
	}

	/// The user's words travel in the question, the model's tool arguments, the tool's result and the answer: whatever
	/// attribute carries them out of the app went through the masker.
	@Test
	void everyTextOfAQuestionLeavesTheAppOnlyMasked() {
		var personalData = "Kowalski";
		var session = login(users.create(Role.USER));
		masker.masking(personalData);
		model.script(toolCall("getTicket", "{\"reference\":\"" + personalData + "\"}"),
				answer("There is no ticket " + personalData + "."));

		var reply = ask(session, "What did " + personalData + " write?");

		assertThat(reply.names()).last().isEqualTo("done");
		var attributes = questionTrace().stream()
				.flatMap(span -> span.getAttributes().asMap().entrySet().stream()
						.map(attribute -> span.getName() + " " + attribute.getKey() + "=" + attribute.getValue()))
				.toList();
		assertThat(attributes).noneMatch(attribute -> attribute.contains(personalData));
		assertThat(attributes).filteredOn(attribute -> attribute.contains(StubPersonalDataMasker.MASKED))
				.hasSizeGreaterThanOrEqualTo(4);
	}

	/// Without Presidio in the app, its spans leave with the texts as they are, and a masker that would fail withholds
	/// nothing (chat ADR 0018).
	@Test
	void withoutPresidioInTheAppTheTextsLeaveUnmasked() {
		settings.change(new ChatSettingsChange(true, false, true, false, "chat-gemini", null, null));
		var personalData = "Kowalski";
		var session = login(users.create(Role.USER));
		masker.failing(true);
		model.script(toolCall("getTicket", "{\"reference\":\"" + personalData + "\"}"),
				answer("There is no ticket " + personalData + "."));

		var reply = ask(session, "What did " + personalData + " write?");

		assertThat(reply.names()).last().isEqualTo("done");
		var attributes = questionTrace().stream()
				.flatMap(span -> span.getAttributes().asMap().values().stream().map(String::valueOf))
				.toList();
		assertThat(attributes).anyMatch(attribute -> attribute.contains(personalData))
				.noneMatch(attribute -> attribute.contains(MaskingSpanExporter.WITHHELD));
	}

	@Test
	void aMaskerThatFailsWithholdsTheTextsAndTheAnswerStillEndsWithDone() {
		var session = login(users.create(Role.USER));
		masker.failing(true);
		model.script(toolCall("searchTickets", "{}"), answer(ANSWER));

		var reply = ask(session, QUESTION);

		assertThat(reply.names()).last().isEqualTo("done");
		assertThat(reply.text()).isEqualTo(ANSWER);
		var spans = questionTrace();
		var withTexts = spans.stream().filter(span -> operation(span) != null && span.getAttributes()
				.asMap().keySet().stream().anyMatch(TraceText.KEYS::contains)).toList();
		assertThat(withTexts).hasSize(3);
		for (var span : withTexts) {
			for (var key : TraceText.KEYS) {
				var text = span.getAttributes().get(key);
				assertThat(text).as("%s on %s", key, span.getName()).isIn(null, MaskingSpanExporter.WITHHELD);
			}
			assertThat(text(span, "retrofit.prompt.version")).isEqualTo(systemPrompt.version());
		}
	}

	@Test
	void withTheSwitchOffAQuestionExportsNoSpan() {
		var session = login(users.create(Role.USER));
		settings.change(new ChatSettingsChange(false, false, true, true, "chat-gemini", null, null));
		model.script(toolCall("searchTickets", "{}"), answer(ANSWER));

		var reply = ask(session, QUESTION);

		assertThat(reply.names()).last().isEqualTo("done");
		await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
			tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
			assertThat(exported.getFinishedSpanItems()).isEmpty();
		});
	}

	@Test
	void aRequestForTheChatSettingsIsNotTraced() {
		var session = login(users.create(Role.SUPPORT));
		var settingsRead = http().get().uri("/api/chat/settings")
				.header(HttpHeaders.COOKIE, "SESSION=" + session)
				.retrieve()
				.toBodilessEntity();
		assertThat(settingsRead.getStatusCode()).isEqualTo(HttpStatus.OK);
		model.script(answer(ANSWER));

		ask(session, QUESTION);

		assertThat(questionTrace()).noneSatisfy(span -> assertThat(text(span, "uri")).isEqualTo("/api/chat/settings"));
	}

	/// The spans exported since the test began, once the question's HTTP request is among them: the request span
	/// ends when the stream completes, which can be just after the client read its last event.
	private List<SpanData> questionTrace() {
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
			assertThat(exported.getFinishedSpanItems()).anySatisfy(span -> assertThat(isChatRequest(span)).isTrue());
		});
		return exported.getFinishedSpanItems();
	}

	private static SpanData httpRequest(List<SpanData> spans) {
		return spans.stream().filter(ChatTracingTests::isChatRequest).findFirst().orElseThrow();
	}

	private static boolean isChatRequest(SpanData span) {
		return "/api/chat/conversations/{conversationId}/messages".equals(text(span, "uri"));
	}

	private static String operation(SpanData span) {
		return text(span, "gen_ai.operation.name");
	}

	private static String text(SpanData span, String key) {
		return span.getAttributes().get(stringKey(key));
	}

	private static JsonNode json(SpanData span, String key) {
		return JSON.readTree(text(span, key));
	}

	private static List<SpanData> concat(List<SpanData> first, List<SpanData> second) {
		return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
	}

}
