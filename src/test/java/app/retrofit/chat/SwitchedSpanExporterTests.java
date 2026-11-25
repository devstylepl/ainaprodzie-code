package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/// The settings decide at export time whether a batch of the app's spans leaves, and whether masked, and settings it
/// cannot read count as off.
class SwitchedSpanExporterTests {

	private final InMemorySpanExporter exported = InMemorySpanExporter.create();

	@Test
	void withPresidioInTheAppTheBatchLeavesMasked() {
		var masked = new MaskingSpanExporter(exported, text -> "masked");

		var result = new SwitchedSpanExporter(masked, exported,
				() -> new ChatSettings(true, false, true, true, "chat-gemini", Fallback.AT_ONCE, ReasoningEffort.OFF))
				.export(spans(2));

		assertThat(result.isSuccess()).isTrue();
		assertThat(exported.getFinishedSpanItems()).hasSize(2).allSatisfy(span -> assertThat(
				span.getAttributes().asMap().values()).doesNotContain("Mail jan@acme.example"));
	}

	@Test
	void withoutPresidioInTheAppTheBatchLeavesAsItIsWithoutCallingTheMasker() {
		var masked = new MaskingSpanExporter(exported, text -> {
			throw new AssertionError("The masker was called for " + text);
		});

		var result = new SwitchedSpanExporter(masked, exported,
				() -> new ChatSettings(true, false, true, false, "chat-gemini", Fallback.AT_ONCE, ReasoningEffort.OFF))
				.export(spans(2));

		assertThat(result.isSuccess()).isTrue();
		assertThat(exported.getFinishedSpanItems()).hasSize(2).allSatisfy(span -> assertThat(
				span.getAttributes().asMap().values()).contains("Mail jan@acme.example"));
	}

	@Test
	void withTheSwitchOffTheBatchIsDroppedAsASuccessWithoutCallingTheDelegate() {
		var delegate = new MaskingSpanExporter(exported, text -> {
			throw new AssertionError("The masker was called for " + text);
		});

		var result = new SwitchedSpanExporter(delegate, exported,
				() -> new ChatSettings(false, false, true, true, "chat-gemini", Fallback.AT_ONCE, ReasoningEffort.OFF))
				.export(spans(2));

		assertThat(result.isSuccess()).isTrue();
		assertThat(exported.getFinishedSpanItems()).isEmpty();
	}

	@Test
	void aSwitchThatCannotBeReadDropsTheBatch() {
		Supplier<ChatSettings> failing = () -> {
			throw new IllegalStateException("The database is down");
		};

		var result = new SwitchedSpanExporter(exported, exported, failing).export(spans(2));

		assertThat(result.isSuccess()).isTrue();
		assertThat(exported.getFinishedSpanItems()).isEmpty();
	}

	@Test
	void flushAndShutdownReachTheDelegateWhateverTheSwitchSays() {
		var calls = new ArrayList<String>();
		var delegate = new SpanExporter() {
			@Override
			public CompletableResultCode export(Collection<SpanData> spans) {
				return CompletableResultCode.ofSuccess();
			}

			@Override
			public CompletableResultCode flush() {
				calls.add("flush");
				return CompletableResultCode.ofSuccess();
			}

			@Override
			public CompletableResultCode shutdown() {
				calls.add("shutdown");
				return CompletableResultCode.ofSuccess();
			}
		};
		var gate = new SwitchedSpanExporter(delegate, delegate,
				() -> new ChatSettings(false, false, true, false, "chat-gemini", Fallback.AT_ONCE, ReasoningEffort.OFF));

		gate.flush();
		gate.shutdown();

		assertThat(calls).containsExactly("flush", "shutdown");
	}

	private static List<SpanData> spans(int count) {
		var collected = InMemorySpanExporter.create();
		var tracer = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(collected)).build()
				.get("test");
		for (int i = 0; i < count; i++) {
			tracer.spanBuilder("chat chat-gemini").setAttribute("gen_ai.input.messages", "Mail jan@acme.example")
					.startSpan()
					.end();
		}
		return collected.getFinishedSpanItems();
	}

}
