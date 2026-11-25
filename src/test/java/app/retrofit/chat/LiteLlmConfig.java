package app.retrofit.chat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/// `litellm/config.yaml` as the tests that hold its aliases to a rule read it.
final class LiteLlmConfig {

	/// The access group `litellm/app_key.py` gives the app's key.
	static final String APP_GROUP = "app";

	private LiteLlmConfig() {
	}

	static Map<String, Object> read() {
		try {
			return new Yaml().load(Files.readString(ChatEvalConfiguration.LITELLM_CONFIG));
		}
		catch (IOException ex) {
			throw new IllegalStateException(ChatEvalConfiguration.LITELLM_CONFIG + " cannot be read", ex);
		}
	}

	@SuppressWarnings("unchecked")
	static List<Map<String, Object>> models() {
		return (List<Map<String, Object>>) read().get("model_list");
	}

	/// The aliases of the app's access group.
	static List<Map<String, Object>> appModels() {
		return models().stream().filter(model -> groups(model).contains(APP_GROUP)).toList();
	}

	static String alias(Map<String, Object> model) {
		return (String) model.get("model_name");
	}

	@SuppressWarnings("unchecked")
	static List<String> groups(Map<String, Object> model) {
		var info = (Map<String, Object>) model.getOrDefault("model_info", Map.of());
		return (List<String>) info.getOrDefault("access_groups", List.of());
	}

	/// What the alias's `litellm_params` list under `name`, such as its `guardrails`.
	@SuppressWarnings("unchecked")
	static List<String> parameterList(Map<String, Object> model, String name) {
		var parameters = (Map<String, Object>) model.getOrDefault("litellm_params", Map.of());
		return (List<String>) parameters.getOrDefault(name, List.of());
	}

}
