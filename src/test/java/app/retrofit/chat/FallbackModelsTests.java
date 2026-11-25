package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/// The fallback models of LiteLLM's `config.yaml`: the alias a question goes to when the provider of its own alias
/// fails (chat ADR 0019).
class FallbackModelsTests {

	@Test
	void geminiFallsBackToAnthropicAndChaosToGemini() throws IOException {
		assertThat(fallbacks()).isEqualTo(Map.of("chat-gemini", List.of("chat-anthropic"), "chat-chaos",
				List.of("chat-gemini")));
	}

	/// LiteLLM starts with a fallback that names no alias and fails the question only when it falls back.
	@Test
	void everyFallbackIsAnAliasOfTheModelList() throws IOException {
		assertThat(fallbacks().values()).isNotEmpty()
			.allSatisfy(fallbacks -> assertThat(aliases()).containsAll(fallbacks));
	}

	/// `router_settings.fallbacks` is a list of one-entry maps, alias to its fallbacks in order.
	@SuppressWarnings("unchecked")
	private static Map<String, List<String>> fallbacks() throws IOException {
		var router = (Map<String, Object>) config().get("router_settings");
		var entries = (List<Map<String, List<String>>>) router.getOrDefault("fallbacks", List.of());
		var fallbacks = new LinkedHashMap<String, List<String>>();
		entries.forEach(fallbacks::putAll);
		return fallbacks;
	}

	@SuppressWarnings("unchecked")
	private static List<String> aliases() throws IOException {
		return ((List<Map<String, Object>>) config().get("model_list")).stream()
			.map(entry -> (String) entry.get("model_name"))
			.toList();
	}

	private static Map<String, Object> config() throws IOException {
		return new Yaml().load(Files.readString(Path.of("litellm", "config.yaml")));
	}

}
