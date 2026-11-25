package app.retrofit.chat;

import org.jspecify.annotations.Nullable;

/// Whether a question asks the model to reason before it answers, and how hard (chat ADR 0020). It holds only efforts
/// every alias of `litellm/config.yaml` accepts: an alias that rejects one answers 400, and a fallback model then
/// answers in its place, so nobody sees that the effort never applied. Spelled exactly as the
/// `chat_setting_reasoning_check` constraint spells it.
enum ReasoningEffort {

	/// The request names no effort, so it is the request the chat sent before the setting existed.
	OFF(null),

	MEDIUM("medium"),

	HIGH("high");

	private final @Nullable String effort;

	ReasoningEffort(@Nullable String effort) {
		this.effort = effort;
	}

	/// The `reasoning_effort` of a model request, or none to leave the field out.
	@Nullable String effort() {
		return effort;
	}

}
