package app.retrofit.chat;

import java.net.URI;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SpanExporters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import okhttp3.Request;
import tools.jackson.databind.json.JsonMapper;

/// The chat's traces (chat ADR 0007). Spring AI's observations become spans through the Micrometer OpenTelemetry
/// bridge, and Spring Boot exports them over OTLP to the endpoint in `application.yml`; the chat adds its attributes
/// and texts to them, keeps other requests out, and masks every batch before it leaves.
@Configuration(proxyBeanMethods = false)
class ChatTracingConfiguration {

	private static final String CHAT_PATHS = "/api/chat/conversations/";

	private static final String TRACEPARENT = "traceparent";

	/// After [StreamedCacheUsage], so a span whose tokens it leaves to LiteLLM keeps no cache counts either.
	@Bean
	@Order(1)
	ChatTraceContentFilter chatTraceContentFilter(SystemPrompt systemPrompt, JsonMapper json) {
		return new ChatTraceContentFilter(systemPrompt, json);
	}

	@Bean
	@Order(0)
	StreamedCacheUsage streamedCacheUsage() {
		return new StreamedCacheUsage();
	}

	/// Wraps the model Spring AI configures, so that every `ChatClient` built on it, the chat's and the evaluations'
	/// judge's, streams through [StreamedCacheUsage]. Static, as a post-processor must be, and with the filter looked up
	/// only when the model is created, so the post-processor brings nothing else up early.
	@Bean
	static BeanPostProcessor streamedCacheUsageModel(ObjectProvider<StreamedCacheUsage> cacheUsage) {
		return new BeanPostProcessor() {

			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) {
				return bean instanceof OpenAiChatModel model ? cacheUsage.getObject().recording(model) : bean;
			}

		};
	}

	/// Only the chat's conversation requests are observed, so the traces show the chat's questions and not every page
	/// load or settings change.
	/// The predicate turns off the whole observation, so other requests record no `http.server.requests` metrics
	/// either; nothing reads them while the app exports no metrics.
	@Bean
	ObservationPredicate onlyChatRequests() {
		return (name, context) -> !(context instanceof ServerRequestObservationContext request)
				|| request.getCarrier().getRequestURI().startsWith(CHAT_PATHS);
	}

	@Bean
	OpenModelCalls openModelCalls() {
		return new OpenModelCalls();
	}

	/// Puts LiteLLM's span of a model call under the app's span of that call. Spring AI builds the request, headers
	/// included, before it starts the model observation, so [ChatService] can only send the question's `traceparent`;
	/// this interceptor rewrites it to the model call [OpenModelCalls] has open in that trace. A request sent without a
	/// `traceparent` (the switch off, or no request span) stays without one.
	@Bean
	OpenAiHttpClientBuilderCustomizer modelCallTraceparent(OpenModelCalls calls, Propagator propagator) {
		return builder -> builder.interceptor(chain -> {
			var request = chain.request();
			var traceparent = request.header(TRACEPARENT);
			var modelCall = traceparent == null ? null : calls.in(traceparent.split("-")[1]);
			if (modelCall == null) {
				return chain.proceed(request);
			}
			var rewritten = request.newBuilder();
			propagator.inject(modelCall, rewritten, Request.Builder::header);
			return chain.proceed(rewritten.build());
		});
	}

	@Bean
	PresidioMasker presidioMasker(@Value("${retrofit.chat.presidio.analyzer-url}") URI analyzerUrl,
			@Value("${retrofit.chat.presidio.anonymizer-url}") URI anonymizerUrl,
			@Value("classpath:presidio/requests.yaml") Resource requests, JsonMapper json) {
		return new PresidioMasker(analyzerUrl, anonymizerUrl, PresidioRequests.read(requests), json);
	}

	/// Replaces Spring Boot's list of exporters: every exporter, the OTLP one included, receives each batch only while
	/// the stored switch exports the app's traces ([SwitchedSpanExporter]), after [MaskingSpanExporter] masked it once
	/// while the app calls Presidio itself. The store is looked up only when a batch is exported, so the tracer provider
	/// brings no datasource up early.
	@Bean
	SpanExporters spanExporters(ObjectProvider<SpanExporter> exporters, PersonalDataMasker masker,
			ObjectProvider<ChatSettingStore> settings) {
		var all = exporters.orderedStream().toList();
		if (all.isEmpty()) {
			return SpanExporters.of();
		}
		var unmasked = SpanExporter.composite(all);
		return SpanExporters.of(new SwitchedSpanExporter(new MaskingSpanExporter(unmasked, masker), unmasked,
				() -> settings.getObject().settings()));
	}

}
