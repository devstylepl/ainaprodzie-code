package app.retrofit.chat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/// The report of one evaluation run (chat ADR 0003). It starts with the prompt's name and version (chat ADR 0008), so
/// two runs on different prompts can be compared case by case, and with the aliases of the model and of its judge,
/// each followed by the provider model that answered for it in this run: an alias says which entry of LiteLLM's
/// config was asked, and that entry's model changes with the file. It gives each case's outcome and what it spent:
/// the model requests, the judge's included, their tokens and the time the case took, so that runs on different
/// models compare on cost and speed too.
final class ChatEvalReport {

	private static final int MAX_FAILURE_LENGTH = 300;

	private final List<Line> lines = new ArrayList<>();

	/// The provider models that answered for each alias, in the order they first answered.
	private final Map<String, Set<String>> providerModels = new HashMap<>();

	private @Nullable Asked asked;

	/// What one case spent. Cached input tokens are part of the input tokens, the ones the provider read from its
	/// prompt cache; cache write tokens are the ones it wrote to it, which a provider such as Anthropic prices above
	/// an ordinary input token.
	record Spend(int modelRequests, long inputTokens, long cachedInputTokens, long cacheWriteTokens,
			long outputTokens, Duration duration) {

		static final Spend NONE = new Spend(0, 0, 0, 0, 0, Duration.ZERO);

		Spend plus(Spend other) {
			return new Spend(modelRequests + other.modelRequests, inputTokens + other.inputTokens,
					cachedInputTokens + other.cachedInputTokens, cacheWriteTokens + other.cacheWriteTokens,
					outputTokens + other.outputTokens, duration.plus(other.duration));
		}

	}

	private record Line(String name, @Nullable String failure, Spend spend) {
	}

	private record Asked(SystemPrompt prompt, String modelAlias, String judgeAlias) {
	}

	void prompt(SystemPrompt prompt, String modelAlias, String judgeAlias) {
		asked = new Asked(prompt, modelAlias, judgeAlias);
	}

	/// A call of `alias` in this run was answered by `providerModel`, as the gateway named it.
	void answered(String alias, String providerModel) {
		providerModels.computeIfAbsent(alias, each -> new LinkedHashSet<>()).add(providerModel);
	}

	/// The aliases the first line names, the chat's and the judge's; none while the context did not start.
	List<String> aliases() {
		return asked == null ? List.of() : List.of(asked.modelAlias(), asked.judgeAlias());
	}

	void passed(String name, Spend spend) {
		lines.add(new Line(name, null, spend));
	}

	void failed(String name, Spend spend, Throwable failure) {
		var message = Objects.requireNonNullElse(failure.getMessage(), failure.getClass().getSimpleName())
				.replaceAll("\\s+", " ")
				.strip();
		if (message.length() > MAX_FAILURE_LENGTH) {
			message = message.substring(0, MAX_FAILURE_LENGTH) + "…";
		}
		lines.add(new Line(name, message, spend));
	}

	String render() {
		var text = new StringBuilder(firstLine())
				.append("\n\n| Case | Result | Model requests | Input tokens | Cached input tokens | Cache write tokens"
						+ " | Output tokens | Seconds |\n|---|---|---|---|---|---|---|---|\n");
		for (var line : lines) {
			var result = line.failure() == null ? "passed" : "failed: " + line.failure().replace("|", "\\|");
			var spend = line.spend();
			text.append("| ").append(line.name()).append(" | ").append(result).append(" | ")
					.append(spend.modelRequests()).append(" | ").append(spend.inputTokens()).append(" | ")
					.append(spend.cachedInputTokens()).append(" | ").append(spend.cacheWriteTokens()).append(" | ")
					.append(spend.outputTokens()).append(" | ")
					.append(seconds(spend.duration())).append(" |\n");
		}
		var passed = lines.stream().filter(line -> line.failure() == null).count();
		var total = lines.stream().map(Line::spend).reduce(Spend.NONE, Spend::plus);
		return text.append('\n')
				.append(passed).append(" of ").append(lines.size()).append(" cases passed, ")
				.append(total.modelRequests()).append(" model requests, ")
				.append(total.inputTokens()).append(" input tokens (")
				.append(total.cachedInputTokens()).append(" cached, ")
				.append(total.cacheWriteTokens()).append(" written to the cache), ")
				.append(total.outputTokens()).append(" output tokens, ")
				.append(seconds(total.duration())).append(" s.\n")
				.toString();
	}

	/// Composed when the report is rendered, because the models are known only after the calls.
	private String firstLine() {
		if (asked == null) {
			return "Prompt unknown: the context did not start";
		}
		return "Prompt " + asked.prompt().name() + ", version " + asked.prompt().version() + ", model alias "
				+ withProviderModel(asked.modelAlias()) + ", judge alias " + withProviderModel(asked.judgeAlias());
	}

	private String withProviderModel(String alias) {
		var models = providerModels.getOrDefault(alias, Set.of());
		var providerModel = switch (models.size()) {
			case 0 -> "not called";
			case 1 -> models.iterator().next();
			default -> "mixed: " + String.join(", ", models);
		};
		return alias + " (" + providerModel + ")";
	}

	private static String seconds(Duration duration) {
		return String.format(Locale.ROOT, "%.1f", duration.toMillis() / 1000.0);
	}

}
