package app.retrofit.chat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.util.StringUtils;

import com.openai.core.JsonValue;
import com.openai.models.completions.CompletionUsage;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import reactor.core.publisher.Flux;

/// Puts the cache usage of a streamed model call back on its observation, where Spring AI loses it: it aggregates the
/// stream's chunks into one response whose usage has no cache read (spring-projects/spring-ai#6667), and it never reads
/// a cache write from the OpenAI client (`OpenAiChatModel.getDefaultUsage` passes `null`). Remove this class once the
/// Spring AI release in `pom.xml` fixes #6667 and reads the cache write too: the attributes and the usage carry the same
/// names, so the traces and the evaluations' report stay as they are. With #6667 fixed alone, they would lose the cache
/// write.
///
/// The model [#recording] wraps records each chunk's cache read and cache write under the chunk's response id: the
/// read from its usage, the write as LiteLLM reports it, `cache_creation_input_tokens` in the provider's usage. The
/// filter then puts both on the model call's observation, as Spring AI's attributes and in the response's usage, which
/// every handler reads after the filters ran: the tracing handler, and the evaluations' report.
///
/// The wrapper sees each chunk before Spring AI completes its aggregate and stops the observation, so the entry is
/// there when the filter runs. Entries of streams that were cancelled or failed are never taken, so the map keeps only
/// the latest ones.
///
/// Only streamed calls are covered. A call that does not stream keeps its cache read but still has no cache write: its
/// observation stops inside the model, before the wrapper sees the response.
final class StreamedCacheUsage implements ObservationFilter {

	private static final String CACHE_WRITE = "cache_creation_input_tokens";

	/// More model calls than can be streaming at once.
	private static final int MAX_ENTRIES = 256;

	/// What the provider read from its prompt cache and wrote to it, each when it said.
	private record Cache(@Nullable Long read, @Nullable Long write) {

		/// This cache usage, with what `earlier` said in place of what this one leaves out.
		Cache or(Cache earlier) {
			return new Cache(read != null ? read : earlier.read(), write != null ? write : earlier.write());
		}

	}

	private final Map<String, Cache> byResponseId = Collections.synchronizedMap(new LinkedHashMap<>() {

		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Cache> eldest) {
			return size() > MAX_ENTRIES;
		}

	});

	/// `model`, with the cache usage of every response it streams recorded for this filter.
	ChatModel recording(ChatModel model) {
		return new Recording(model);
	}

	@Override
	public Observation.Context map(Observation.Context context) {
		if (!(context instanceof ChatModelObservationContext model) || model.getResponse() == null) {
			return context;
		}
		var response = model.getResponse();
		var cache = byResponseId.remove(response.getMetadata().getId());
		if (cache == null) {
			return context;
		}
		if (cache.read() != null) {
			add(model, HighCardinalityKeyNames.USAGE_CACHE_READ_INPUT_TOKENS, cache.read());
		}
		if (cache.write() != null) {
			add(model, HighCardinalityKeyNames.USAGE_CACHE_WRITE_INPUT_TOKENS, cache.write());
		}
		model.setResponse(withCache(response, cache));
		return context;
	}

	private void remember(ChatResponse chunk) {
		var id = chunk.getMetadata().getId();
		var usage = chunk.getMetadata().getUsage();
		var cache = new Cache(usage.getCacheReadInputTokens(), cacheWrite(usage));
		if (StringUtils.hasText(id) && (cache.read() != null || cache.write() != null)) {
			byResponseId.merge(id, cache, Cache::or);
		}
	}

	private static @Nullable Long cacheWrite(Usage usage) {
		return usage.getNativeUsage() instanceof CompletionUsage completion
				&& completion._additionalProperties().get(CACHE_WRITE) instanceof JsonValue value
				&& value.asNumber().orElse(null) instanceof Number tokens ? tokens.longValue() : null;
	}

	private static void add(Observation.Context context, HighCardinalityKeyNames key, long tokens) {
		context.addHighCardinalityKeyValue(KeyValue.of(key.asString(), String.valueOf(tokens)));
	}

	private static ChatResponse withCache(ChatResponse response, Cache cache) {
		var usage = response.getMetadata().getUsage();
		var merged = cache.or(new Cache(usage.getCacheReadInputTokens(), usage.getCacheWriteInputTokens()));
		return UsageCalculator.withUsage(response, new DefaultUsage(usage.getPromptTokens(),
				usage.getCompletionTokens(), usage.getTotalTokens(), usage.getNativeUsage(), merged.read(),
				merged.write()));
	}

	private final class Recording implements ChatModel {

		private final ChatModel model;

		Recording(ChatModel model) {
			this.model = model;
		}

		@Override
		public ChatResponse call(Prompt prompt) {
			return model.call(prompt);
		}

		@Override
		public Flux<ChatResponse> stream(Prompt prompt) {
			return model.stream(prompt).doOnNext(StreamedCacheUsage.this::remember);
		}

		@Override
		public ChatOptions getOptions() {
			return model.getOptions();
		}

	}

}
