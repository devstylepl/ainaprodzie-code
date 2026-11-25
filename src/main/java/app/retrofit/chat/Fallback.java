package app.retrofit.chat;

import java.util.Map;

/// When a question goes to the fallback model LiteLLM's `config.yaml` names for its alias, after the alias's provider
/// failed with an error or a timeout (chat ADR 0019). The app says it on every model call, in fields LiteLLM reads
/// from the request. Spelled exactly as the `chat_setting_fallback_check` constraint spells it.
enum Fallback {

	/// Never: the provider is retried as `config.yaml` says, and its error ends the answer.
	OFF(Map.of("disable_fallbacks", true)),

	/// After the retry of `config.yaml` failed too.
	AFTER_RETRY(Map.of()),

	/// On the first failure, without a retry.
	AT_ONCE(Map.of("num_retries", 0));

	private final Map<String, Object> requestFields;

	Fallback(Map<String, Object> requestFields) {
		this.requestFields = requestFields;
	}

	/// The fields of a model request that make LiteLLM act so.
	Map<String, Object> requestFields() {
		return requestFields;
	}

}
