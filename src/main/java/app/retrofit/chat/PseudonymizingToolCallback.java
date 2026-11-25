package app.retrofit.chat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import app.retrofit.ticket.AuditedField;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/// A tool that runs with the values of the placeholders the model passes it, and whose result the model reads with its
/// personal data replaced by placeholders: the conversation's, which the [ChatTurn] in the tool context holds (chat
/// ADR 0009).
///
/// The people a result names in its own fields, [#PEOPLE] and the assignees of an assignee change, are persons, and
/// their names are replaced in the result's other texts too, as written. So is the full display name of every person
/// the turn's directory holds, in any text of the result. Nothing else is looked for: an e-mail address or a phone
/// number is the gateway's (chat ADR 0017). Which of this a turn does is the [ChatTurn]'s to say: one that does not
/// protect personal data leaves the people as the result names them (chat ADR 0018).
final class PseudonymizingToolCallback implements ToolCallback {

	/// The fields of [TicketLine] and [TicketLookup] that name a person.
	static final Set<String> PEOPLE = Set.of("author", "assignee", "actor");

	/// The fields of a [TicketLookup.Change] of the assignee that name the assignees before and after it.
	private static final Set<String> ASSIGNEES = Set.of("oldValue", "newValue");

	private final ToolCallback tool;

	private final JsonMapper json;

	PseudonymizingToolCallback(ToolCallback tool, JsonMapper json) {
		this.tool = tool;
		this.json = json;
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return tool.getToolDefinition();
	}

	@Override
	public ToolMetadata getToolMetadata() {
		return tool.getToolMetadata();
	}

	@Override
	public String call(String toolInput) {
		return call(toolInput, new ToolContext(Map.of()));
	}

	@Override
	public String call(String toolInput, ToolContext toolContext) {
		var turn = ChatTurn.of(toolContext);
		var result = json.readTree(tool.call(restore(toolInput, turn), toolContext));
		return json.writeValueAsString(replace(result, turn.forModel(texts(result), people(result)).iterator()));
	}

	/// Arguments that are not a JSON object go to the tool as they are, which refuses them as it would without
	/// placeholders.
	private String restore(String toolInput, ChatTurn turn) {
		return ToolCallArguments.parse(json, toolInput)
				.map(input -> json.writeValueAsString(restore(input, turn)))
				.orElse(toolInput);
	}

	/// The arguments with the values of the placeholders the model wrote in them.
	static JsonNode restore(ObjectNode arguments, ChatTurn turn) {
		return replace(arguments, texts(arguments).stream().map(turn::fromModel).iterator());
	}

	private static List<String> people(JsonNode node) {
		var people = new ArrayList<String>();
		var field = node.get("field");
		var assigneeChange = field != null && field.isString() && field.asString().equals(AuditedField.ASSIGNEE.name());
		for (var property : node.properties()) {
			var namesPerson = PEOPLE.contains(property.getKey())
					|| assigneeChange && ASSIGNEES.contains(property.getKey());
			if (namesPerson && property.getValue().isString()) {
				people.add(property.getValue().asString());
			}
		}
		node.values().forEach(child -> people.addAll(people(child)));
		return people;
	}

	private static List<String> texts(JsonNode node) {
		var texts = new ArrayList<String>();
		collect(node, texts);
		return texts;
	}

	private static void collect(JsonNode node, List<String> texts) {
		if (node.isString()) {
			texts.add(node.asString());
		}
		node.values().forEach(child -> collect(child, texts));
	}

	/// The same tree with its texts taken, in the order [#collect] found them, from `texts`.
	private static JsonNode replace(JsonNode node, Iterator<String> texts) {
		return switch (node) {
			case StringNode _ -> StringNode.valueOf(texts.next());
			case ObjectNode object -> {
				var copy = object.objectNode();
				object.properties().forEach(property -> copy.set(property.getKey(), replace(property.getValue(), texts)));
				yield copy;
			}
			case ArrayNode array -> {
				var copy = array.arrayNode();
				array.values().forEach(element -> copy.add(replace(element, texts)));
				yield copy;
			}
			default -> node;
		};
	}

}
