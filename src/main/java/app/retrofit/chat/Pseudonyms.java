package app.retrofit.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The placeholders of one conversation, such as `<PERSON_1>`: one value has one placeholder in every turn, so the
/// model can tell two people apart and follow one through the conversation (chat ADR 0009). A value is a person: one
/// a tool result names in its own fields, or one the [PeopleDirectory] finds by their display name.
final class Pseudonyms {

	/// A placeholder as a model may write it: Markdown lets it escape the underscores, as in `<PERSON\_1>`.
	private static final Pattern PLACEHOLDER = Pattern.compile("<[A-Z]+(\\\\?_[A-Z]+)*\\\\?_\\d+>");

	/// The end of a text that may be the start of a placeholder whose end has not arrived yet.
	private static final Pattern UNFINISHED = Pattern.compile("<[A-Z_0-9\\\\]*$");

	/// The type of a person, which gets a placeholder such as `<PERSON_1>`.
	private static final String PERSON = "PERSON";

	/// The type of a text that looks like a placeholder, which gets one such as `<PLACEHOLDER_1>`.
	private static final String LOOKALIKE = "PLACEHOLDER";

	private final Map<String, String> placeholders = new HashMap<>();

	private final Map<String, String> values = new HashMap<>();

	private final Map<String, Integer> numbers = new HashMap<>();

	/// A value of one type and where the text writes it, `end` exclusive.
	private record Span(String type, String value, int start, int end) {
	}

	/// The text with each person the directory finds replaced by the placeholder of their display name, and every value
	/// this conversation has a placeholder for too, wherever it stands as a whole word. Of spans that overlap, the one
	/// that starts first is kept, and the longer one of two that start together.
	///
	/// A text that only looks like a placeholder, such as `<PERSON_1>` in a pasted log, is a value of its own too, of
	/// the type [#LOOKALIKE], so [#restore] gives the user that text back, never the value of the conversation's own
	/// `<PERSON_1>`.
	synchronized String pseudonymize(String text, PeopleDirectory directory) {
		var all = new ArrayList<Span>();
		directory.find(text).forEach(match -> all.add(new Span(PERSON, match.name(), match.start(), match.end())));
		all.addAll(knownValues(text));
		all.addAll(lookalikes(text));
		return replaced(text, all);
	}

	/// The text with nothing replaced but what looks like a placeholder, as [#pseudonymize] replaces it: for a
	/// question that protects no personal data (chat ADR 0018), where [#restore] still has to tell the conversation's
	/// placeholders from a text the model only read.
	synchronized String pseudonymizeLookalikes(String text) {
		return replaced(text, lookalikes(text));
	}

	private static List<Span> lookalikes(String text) {
		return PLACEHOLDER.matcher(text).results()
				.map(match -> new Span(LOOKALIKE, match.group(), match.start(), match.end()))
				.toList();
	}

	private String replaced(String text, List<Span> spans) {
		var ordered = spans.stream()
				.sorted(Comparator.comparingInt(Span::start)
						.thenComparing(Comparator.comparingInt((Span span) -> span.end()).reversed()))
				.toList();
		var result = new StringBuilder();
		var position = 0;
		for (var span : ordered) {
			if (span.start() < position || span.value().isBlank()) {
				continue;
			}
			result.append(text, position, span.start()).append(placeholder(span.type(), span.value()));
			position = span.end();
		}
		return result.append(text, position, text.length()).toString();
	}

	/// The text with each placeholder of this conversation replaced by its value. A placeholder the conversation never
	/// gave out stays as it is.
	synchronized String restore(String text) {
		return PLACEHOLDER.matcher(text).replaceAll(match -> Matcher.quoteReplacement(
				values.getOrDefault(match.group().replace("\\_", "_"), match.group())));
	}

	/// Gives `name` a placeholder of a person, if it has none yet. A blank name gets none: as a known value it would
	/// match the gaps between words, and an empty one would never stop matching.
	synchronized void person(String name) {
		if (!name.isBlank()) {
			placeholder(PERSON, name);
		}
	}

	private List<Span> knownValues(String text) {
		var found = new ArrayList<Span>();
		placeholders.forEach((value, placeholder) -> {
			for (var start = text.indexOf(value); start >= 0; start = text.indexOf(value, start + 1)) {
				var end = start + value.length();
				if (!wordCharacterAt(text, start - 1) && !wordCharacterAt(text, end)) {
					found.add(new Span(type(placeholder), value, start, end));
				}
			}
		});
		return found;
	}

	private static boolean wordCharacterAt(String text, int index) {
		return index >= 0 && index < text.length() && Character.isLetterOrDigit(text.charAt(index));
	}

	/// `PERSON` of `<PERSON_1>`.
	private static String type(String placeholder) {
		return placeholder.substring(1, placeholder.lastIndexOf('_'));
	}

	/// Where a placeholder the text ends with, unfinished, starts, looking from `from` on: the text's length when it
	/// ends with none.
	static int unfinishedPlaceholderStart(CharSequence text, int from) {
		var matcher = UNFINISHED.matcher(text).region(from, text.length());
		return matcher.find() ? matcher.start() : text.length();
	}

	private String placeholder(String type, String value) {
		return placeholders.computeIfAbsent(value, _ -> {
			var placeholder = "<" + type + "_" + numbers.merge(type, 1, Integer::sum) + ">";
			values.put(placeholder, value);
			return placeholder;
		});
	}

}
