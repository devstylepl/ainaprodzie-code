package app.retrofit.chat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/// What the app asks Presidio for, read from `presidio/requests.yaml`: the `analyzer` section, sent with the text
/// added, and the `anonymizer` section, sent with the text and the analyzer's findings added. Only the file's shape is
/// checked here; Presidio judges its own fields, and [PresidioMaskerEval] catches a recognizer that stops finding.
record PresidioRequests(Map<String, Object> analyzer, Map<String, Object> anonymizer) {

	static PresidioRequests read(Resource resource) {
		Object file;
		try (InputStream in = resource.getInputStream()) {
			file = new Yaml().load(in);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("The Presidio requests " + resource + " cannot be read", ex);
		}
		catch (YAMLException ex) {
			throw new IllegalStateException("The Presidio requests " + resource + " are no valid YAML", ex);
		}
		if (!(file instanceof Map<?, ?> sections)) {
			throw new IllegalStateException("The Presidio requests " + resource + " are no map of sections");
		}
		return new PresidioRequests(section(sections, "analyzer", resource), section(sections, "anonymizer", resource));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> section(Map<?, ?> sections, String name, Resource resource) {
		if (!(sections.get(name) instanceof Map<?, ?> section)) {
			throw new IllegalStateException("The Presidio requests " + resource + " have no map under " + name);
		}
		return (Map<String, Object>) section;
	}

}
