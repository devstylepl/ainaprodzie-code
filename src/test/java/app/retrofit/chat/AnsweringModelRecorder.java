package app.retrofit.chat;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import okhttp3.Interceptor;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import tools.jackson.databind.json.JsonMapper;

/// Which provider model answered for each alias the evaluations called (chat ADR 0003). The app knows aliases only
/// (chat ADR 0002), so the recorder reads what LiteLLM says: the alias is `model` in the request's body, and the
/// provider model the response's `x-litellm-model-name`, because the response to a call a fallback answered no
/// longer names the alias that was asked.
///
/// Only an answer counts, a 2xx response that carries the header: a refusal and the SDK's retry of it record nothing.
final class AnsweringModelRecorder {

	static final String PROVIDER_MODEL_HEADER = "x-litellm-model-name";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final Map<String, Set<String>> providerModels = new LinkedHashMap<>();

	/// The provider models that answered since the last call, for each alias in the order they first answered.
	synchronized Map<String, List<String>> take() {
		var taken = new LinkedHashMap<String, List<String>>();
		providerModels.forEach((alias, models) -> taken.put(alias, List.copyOf(models)));
		providerModels.clear();
		return taken;
	}

	private synchronized void answered(String alias, String providerModel) {
		providerModels.computeIfAbsent(alias, each -> new LinkedHashSet<>()).add(providerModel);
	}

	/// The request's body is read once the call returned: the SDK keeps a JSON body in memory, to send it again on a
	/// retry, so it can be written a second time. A body that can be written only once, or that does not name a
	/// model, is left alone, so that the recorder never fails a call.
	private Response record(Interceptor.Chain chain) throws IOException {
		var request = chain.request();
		var response = chain.proceed(request);
		var providerModel = response.header(PROVIDER_MODEL_HEADER);
		var body = request.body();
		if (response.isSuccessful() && providerModel != null && body != null && !body.isOneShot()) {
			var alias = requestedAlias(body);
			if (alias != null) {
				answered(alias, providerModel);
			}
		}
		return response;
	}

	private static @Nullable String requestedAlias(RequestBody body) {
		try {
			var written = new Buffer();
			body.writeTo(written);
			var model = JSON.readTree(written.inputStream()).path("model");
			return model.isString() ? model.asString() : null;
		}
		catch (IOException | RuntimeException ex) {
			return null;
		}
	}

	/// The recorder as an interceptor of the model client, beside the app's own.
	@TestConfiguration(proxyBeanMethods = false)
	static class Wiring {

		@Bean
		AnsweringModelRecorder answeringModelRecorder() {
			return new AnsweringModelRecorder();
		}

		@Bean
		OpenAiHttpClientBuilderCustomizer answeringModel(AnsweringModelRecorder recorder) {
			return builder -> builder.interceptor(recorder::record);
		}

	}

}
