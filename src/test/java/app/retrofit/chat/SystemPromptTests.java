package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import app.retrofit.ticket.AuditedField;
import app.retrofit.ticket.LinkRelation;
import app.retrofit.ticket.TicketCategory;
import app.retrofit.ticket.TicketPriority;
import app.retrofit.ticket.TicketStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// The prompt names every code the tools return by the labels the SPA shows for it, so an answer reads "Czeka na
/// klienta" where the ticket's page does, never `WAITING_FOR_CUSTOMER`. A new code of these groups or a changed label
/// in the SPA's bundles fails here until the prompt follows: in the same pull request, or, for a change made on
/// `master`, in its forward merge into `lesson-1`.
class SystemPromptTests {

	private static final SystemPrompt PROMPT = SystemPrompt.read("chat-system",
			new ClassPathResource("prompts/chat-system.st"));

	/// A line of the prompt's list of labels, such as `- LOW: Low | Niski`.
	private static final Pattern LABEL_LINE = Pattern.compile("- [A-Z_]+: .*");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final JsonNode EN = bundle("en");

	private static final JsonNode PL = bundle("pl");

	/// A missing line leaves the model a code without a label. A label kept beside its renamed successor, or the line
	/// of a removed code, would give the model a label the SPA no longer shows.
	@Test
	void thePromptLabelsEachCodeOnceAsTheInterfaceDoes() {
		assertThat(PROMPT.text().lines().filter(LABEL_LINE.asMatchPredicate()))
				.as("the label lines of src/main/resources/prompts/chat-system.st")
				.containsExactlyInAnyOrderElementsOf(CodeGroup.RETURNED.stream()
						.flatMap(group -> group.codes().stream().map(code -> line(group.key(), code)))
						.toList());
	}

	private static String line(String group, Enum<?> code) {
		return "- %s: %s | %s".formatted(code.name(), label(EN, group, code), label(PL, group, code));
	}

	private static String label(JsonNode bundle, String group, Enum<?> code) {
		var label = bundle.path("tickets").path(group).path(code.name());
		assertThat(label.isString()).as("the SPA's label of %s.%s", group, code).isTrue();
		return label.asString();
	}

	private static JsonNode bundle(String language) {
		return JSON.readTree(Path.of("frontend", "src", "i18n", language, "common.json").toFile());
	}

	/// Codes the tools return, by the key of their labels under `tickets` in the SPA's `common` namespace. [ChatEval]
	/// fails an answer that writes one of them.
	record CodeGroup(String key, List<? extends Enum<?>> codes) {

		/// Every group of codes in the tools' results. The `CREATED` field is not among them: [TicketLookup] leaves
		/// that entry out of a ticket's changes.
		static final List<CodeGroup> RETURNED = List.of(new CodeGroup("status", List.of(TicketStatus.values())),
				new CodeGroup("priority", List.of(TicketPriority.values())),
				new CodeGroup("category", List.of(TicketCategory.values())),
				new CodeGroup("relation", List.of(LinkRelation.values())),
				new CodeGroup("field", Arrays.stream(AuditedField.values())
						.filter(field -> field != AuditedField.CREATED)
						.toList()));

	}

}
