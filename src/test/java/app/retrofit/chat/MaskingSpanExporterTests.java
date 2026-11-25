package app.retrofit.chat;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

/// What leaves the app in a span: the chat's four texts only masked, and only the placeholder when masking fails
/// (chat ADR 0007).
class MaskingSpanExporterTests {

	private static final Map<String, String> TEXTS = Map.of(
			"gen_ai.input.messages", "[{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"content\":\"Mail jan.kowalski@acme.example\"}]}]",
			"gen_ai.output.messages", "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\"Call 512 345 678\"}]}]",
			"gen_ai.tool.call.arguments", "{\"text\":\"jan.kowalski@acme.example\"}",
			"gen_ai.tool.call.result", "{\"description\":\"Remove jan.kowalski@acme.example, phone 512 345 678\"}");

	private final InMemorySpanExporter exported = InMemorySpanExporter.create();

	@Test
	void theFourTextsLeaveMaskedAndEveryOtherAttributeUnchanged() {
		export(text -> text.replace("jan.kowalski@acme.example", "<EMAIL_ADDRESS>")
				.replace("512 345 678", "<PHONE_NUMBER>"));

		var span = exported.getFinishedSpanItems().getFirst();

		assertThat(text(span, "gen_ai.input.messages"))
				.isEqualTo("[{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"content\":\"Mail <EMAIL_ADDRESS>\"}]}]");
		assertThat(text(span, "gen_ai.output.messages"))
				.isEqualTo("[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\"Call <PHONE_NUMBER>\"}]}]");
		assertThat(text(span, "gen_ai.tool.call.arguments")).isEqualTo("{\"text\":\"<EMAIL_ADDRESS>\"}");
		assertThat(text(span, "gen_ai.tool.call.result"))
				.isEqualTo("{\"description\":\"Remove <EMAIL_ADDRESS>, phone <PHONE_NUMBER>\"}");
		assertThat(text(span, "gen_ai.request.model")).isEqualTo("chat-gemini");
		assertThat(text(span, "retrofit.prompt.version")).isEqualTo("0123456789ab");
		assertThat(span.getAttributes().get(longKey("gen_ai.usage.input_tokens"))).isEqualTo(470L);
		assertThat(span.getName()).isEqualTo("chat chat-gemini");
	}

	@Test
	void aTextTheMaskerCannotMaskLeavesAsThePlaceholderAndTheSpanStillLeaves() {
		export(text -> {
			if (text.contains("512 345 678")) {
				throw new PersonalDataMasker.MaskingUnavailable("Presidio timed out", null);
			}
			return text;
		});

		var span = exported.getFinishedSpanItems().getFirst();

		assertThat(text(span, "gen_ai.output.messages")).isEqualTo(MaskingSpanExporter.WITHHELD);
		assertThat(text(span, "gen_ai.tool.call.result")).isEqualTo(MaskingSpanExporter.WITHHELD);
		assertThat(text(span, "gen_ai.input.messages")).isEqualTo(TEXTS.get("gen_ai.input.messages"));
		assertThat(text(span, "gen_ai.tool.call.arguments")).isEqualTo(TEXTS.get("gen_ai.tool.call.arguments"));
		assertThat(text(span, "retrofit.prompt.version")).isEqualTo("0123456789ab");
	}

	@Test
	void aMaskerThatDoesNotAnswerIsCalledOncePerBatchAndEveryTextLeavesAsThePlaceholder() {
		var calls = new AtomicInteger();
		var exporter = new MaskingSpanExporter(exported, text -> {
			calls.incrementAndGet();
			throw new PersonalDataMasker.MaskerNotAnswering("Presidio did not answer", null);
		});

		exporter.export(unmaskedSpans(3));
		exporter.export(unmaskedSpans(3));

		assertThat(calls).hasValue(2);
		assertThat(exported.getFinishedSpanItems()).hasSize(6).allSatisfy(span -> TEXTS.keySet()
				.forEach(key -> assertThat(text(span, key)).isEqualTo(MaskingSpanExporter.WITHHELD)));
	}

	@Test
	void aSpanWithoutTextsLeavesWithoutCallingTheMasker() {
		var tracer = tracerProvider(text -> {
			throw new AssertionError("The masker was called for " + text);
		});

		tracer.get("test").spanBuilder("http post /api/chat").setAttribute("http.route", "/api/chat").startSpan().end();

		assertThat(exported.getFinishedSpanItems()).singleElement()
				.satisfies(span -> assertThat(text(span, "http.route")).isEqualTo("/api/chat"));
	}

	@Test
	void aRecordedFailureLeavesWithItsClassesAndFramesButNoMessage() {
		var question = "Mail jan.kowalski@acme.example";
		var failure = new IllegalStateException("LiteLLM answered 400: " + question + "\nsecond line " + question,
				new IllegalArgumentException(question));
		var span = tracerProvider(text -> text).get("test").spanBuilder("chat chat-gemini").startSpan();
		span.recordException(failure);
		span.setStatus(StatusCode.ERROR, failure.getMessage());
		span.end();

		var exportedSpan = exported.getFinishedSpanItems().getFirst();
		var event = exportedSpan.getEvents().getFirst().getAttributes();

		assertThat(event.get(stringKey("exception.type"))).isEqualTo(IllegalStateException.class.getName());
		assertThat(event.get(stringKey("exception.message"))).isEqualTo(MaskingSpanExporter.MESSAGE_WITHHELD);
		assertThat(event.get(stringKey("exception.stacktrace")))
				.startsWith(IllegalStateException.class.getName() + " " + MaskingSpanExporter.MESSAGE_WITHHELD + "\n\tat ")
				.contains("Caused by: " + IllegalArgumentException.class.getName() + " "
						+ MaskingSpanExporter.MESSAGE_WITHHELD)
				.doesNotContain("acme.example", "second line");
		assertThat(exportedSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
		assertThat(exportedSpan.getStatus().getDescription()).isEqualTo(MaskingSpanExporter.MESSAGE_WITHHELD);
	}

	private void export(PersonalDataMasker masker) {
		var attributes = Attributes.builder()
				.put("gen_ai.request.model", "chat-gemini")
				.put("retrofit.prompt.version", "0123456789ab")
				.put("gen_ai.usage.input_tokens", 470L);
		TEXTS.forEach(attributes::put);
		tracerProvider(masker).get("test").spanBuilder("chat chat-gemini").setAllAttributes(attributes.build())
				.startSpan()
				.end();
	}

	private SdkTracerProvider tracerProvider(PersonalDataMasker masker) {
		return SdkTracerProvider.builder()
				.addSpanProcessor(SimpleSpanProcessor.create(new MaskingSpanExporter(exported, masker)))
				.build();
	}

	/// Spans with the four texts, as the batch span processor hands them to the exporter.
	private static List<SpanData> unmaskedSpans(int count) {
		var collected = InMemorySpanExporter.create();
		var tracer = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(collected)).build()
				.get("test");
		var texts = Attributes.builder();
		TEXTS.forEach(texts::put);
		for (int i = 0; i < count; i++) {
			tracer.spanBuilder("chat chat-gemini").setAllAttributes(texts.build()).startSpan().end();
		}
		return collected.getFinishedSpanItems();
	}

	private static String text(SpanData span, String key) {
		return span.getAttributes().get(stringKey(key));
	}

}
