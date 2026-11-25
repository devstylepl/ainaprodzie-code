package app.retrofit.chat;

import com.fasterxml.jackson.annotation.JsonValue;

/// The code of an `error` event: why an answer ended early. [ChatErrors] chooses it.
enum ChatErrorCode {

	/// LiteLLM answered 429: the provider's quota is spent for now.
	RATE_LIMITED("rate-limited"),

	/// LiteLLM cannot be reached, or it or the provider failed with a 5xx, as it does when its Presidio guardrail
	/// cannot mask a request (chat ADR 0017).
	UNAVAILABLE("unavailable"),

	/// The model did not answer within the client's timeout.
	TIMEOUT("timeout"),

	/// LiteLLM's guardrail refused the request. `personal-data` is the only guardrail that blocks, and a card number
	/// is all it blocks for (chat ADR 0017).
	GUARDRAILS_BLOCKED("guardrails-blocked"),

	/// Anything else: an empty answer, the tool-call limit, an unexpected exception.
	FAILED("failed");

	private final String code;

	ChatErrorCode(String code) {
		this.code = code;
	}

	/// The code as the SPA reads it, and as the contract lists it. Public, because springdoc derives the contract's
	/// values only from a public `@JsonValue` method.
	@JsonValue
	public String code() {
		return code;
	}

}
