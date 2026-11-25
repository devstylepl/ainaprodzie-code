package app.retrofit.chat;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;

/// The regular tests' tracing: the `test` profile turns the OTLP export off, and this in-memory exporter collects what
/// would leave the app instead. Being a `SpanExporter` bean, it sits behind [MaskingSpanExporter] like the OTLP
/// exporter does. The masker is a [StubPersonalDataMasker], because the regular tests run without Presidio.
///
/// A plain `@Configuration` found by component scanning, like [ScriptedChatModelConfiguration].
@Configuration(proxyBeanMethods = false)
@Profile("test")
class ChatTracingTestConfiguration {

	@Bean
	InMemorySpanExporter inMemorySpanExporter() {
		return InMemorySpanExporter.create();
	}

	@Bean
	@Primary
	StubPersonalDataMasker stubPersonalDataMasker() {
		return new StubPersonalDataMasker();
	}

	/// Leaves a text as it is but for the one word it is told to mask, or fails as an unreachable Presidio would.
	static final class StubPersonalDataMasker implements PersonalDataMasker {

		static final String MASKED = "<PERSON>";

		private volatile boolean failing;

		private volatile @Nullable String personalData;

		void failing(boolean failing) {
			this.failing = failing;
		}

		/// Replaces `personalData` with [#MASKED] in every text, or nothing when it is `null`.
		void masking(@Nullable String personalData) {
			this.personalData = personalData;
		}

		@Override
		public String mask(String text) {
			if (failing) {
				throw new MaskingUnavailable("The stub masker is failing", null);
			}
			var word = personalData;
			return word == null ? text : text.replace(word, MASKED);
		}

	}

}
