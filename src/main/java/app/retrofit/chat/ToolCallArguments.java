package app.retrofit.chat;

import java.util.Optional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// The arguments of a tool call, which the model writes as a JSON object, or as nothing for a call without any.
final class ToolCallArguments {

	private ToolCallArguments() {
	}

	/// The object the model wrote, an empty one for blank arguments, or empty when it wrote anything else.
	static Optional<ObjectNode> parse(JsonMapper json, String arguments) {
		if (arguments.isBlank()) {
			return Optional.of(json.createObjectNode());
		}
		try {
			return json.readTree(arguments) instanceof ObjectNode object ? Optional.of(object) : Optional.empty();
		}
		catch (JacksonException ex) {
			return Optional.empty();
		}
	}

}
