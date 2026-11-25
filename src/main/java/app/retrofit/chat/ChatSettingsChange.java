package app.retrofit.chat;

import org.jspecify.annotations.Nullable;

/// The settings one change names. A setting left out, or sent as `null`, stays as stored, so a change of one setting
/// cannot undo what someone else changed in another since the page loaded (chat ADR 0015).
record ChatSettingsChange(@Nullable Boolean appTracesExported, @Nullable Boolean gatewayTracesJoined,
		@Nullable Boolean appLlmSpansExported, @Nullable Boolean presidioInApp, @Nullable String model,
		@Nullable String fallback, @Nullable String reasoning) {
}
