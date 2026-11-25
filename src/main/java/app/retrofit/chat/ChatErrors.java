package app.retrofit.chat;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

import org.jspecify.annotations.Nullable;

import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.RateLimitException;

/// Maps what ended an answer early to the [ChatErrorCode] of its `error` event, the one place that knows the client's
/// exceptions. A cause is looked for along the whole chain, because the client wraps it (a timeout inside an I/O
/// error, everything inside a `CompletionException`).
final class ChatErrors {

	/// How the client's exception starts when LiteLLM's Presidio guardrail refused the request: the status, then
	/// LiteLLM's message up to the entity's type. LiteLLM gives the refusal no code or type of its own, so the message
	/// is all that tells it from another 400.
	private static final String BLOCKED_BY_GUARDRAIL = "400: Blocked entity detected:";

	private ChatErrors() {
	}

	static ChatErrorCode code(Throwable failure) {
		var io = false;
		for (@Nullable Throwable cause = failure; cause != null; cause = cause.getCause()) {
			switch (cause) {
				case RateLimitException _ -> {
					return ChatErrorCode.RATE_LIMITED;
				}
				case TimeoutException _, InterruptedIOException _, HttpTimeoutException _ -> {
					return ChatErrorCode.TIMEOUT;
				}
				case BadRequestException refusal when blockedByGuardrail(refusal) -> {
					return ChatErrorCode.GUARDRAILS_BLOCKED;
				}
				case InternalServerException _ -> {
					return ChatErrorCode.UNAVAILABLE;
				}
				case OpenAIServiceException service when service.statusCode() >= 500 -> {
					return ChatErrorCode.UNAVAILABLE;
				}
				case IOException _, OpenAIIoException _ -> io = true;
				default -> {
				}
			}
		}
		return io ? ChatErrorCode.UNAVAILABLE : ChatErrorCode.FAILED;
	}

	private static boolean blockedByGuardrail(BadRequestException refusal) {
		var message = refusal.getMessage();
		return message != null && message.startsWith(BLOCKED_BY_GUARDRAIL);
	}

	/// The model's answer cannot be shown: it has no text, or the model hit the tool-call limit.
	static final class UnusableAnswer extends RuntimeException {

		UnusableAnswer(String reason) {
			super(reason);
		}

	}

}
