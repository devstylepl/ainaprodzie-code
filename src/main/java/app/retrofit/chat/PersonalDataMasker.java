package app.retrofit.chat;

import org.jspecify.annotations.Nullable;

/// Replaces the personal data in a text with the type of each finding, such as `<EMAIL_ADDRESS>` (chat ADR 0007).
/// [PresidioMasker] is the one the app uses; the regular tests replace it with a stub, because they run without
/// Presidio.
@FunctionalInterface
interface PersonalDataMasker {

	/// The text with its personal data replaced, or [MaskingUnavailable] when it cannot be masked now, which is
	/// [MaskerNotAnswering] when the masker did not answer in time.
	String mask(String text);

	/// The text could not be masked, so it must not leave the app.
	sealed class MaskingUnavailable extends RuntimeException permits MaskerNotAnswering {

		MaskingUnavailable(String message, @Nullable Throwable cause) {
			super(message, cause);
		}

	}

	/// The masker did not answer in time, so the next text would most likely wait as long for nothing.
	final class MaskerNotAnswering extends MaskingUnavailable {

		MaskerNotAnswering(String message, @Nullable Throwable cause) {
			super(message, cause);
		}

	}

}
