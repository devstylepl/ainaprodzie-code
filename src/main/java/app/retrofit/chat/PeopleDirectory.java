package app.retrofit.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/// The people the asker may see, found by their full display name in a text (chat ADR 0009).
///
/// A name matches when every part of it stands in the text in order, side by side, ignoring case, each with an optional
/// Polish case ending, so "Bena Barkera" is "Ben Barker". An ending may replace a final "a" (the genitive "Grety Lind")
/// of a part longer than that letter: alone, the ending would be the word "i". A part with a hyphen is one part, with
/// the ending at its end. A letter or a digit beside the match makes it no match, and so does a hyphen with a letter
/// beyond it ("Ann Lee-Smith" is another person's name). A form that changes the stem ("Grecie") is not matched, nor is
/// one part of a name alone, nor a display name of one word: "Sam" and "Ben" are ordinary words. Where two names match
/// the same text, the one the text writes as it stands wins over a case form of the other.
final class PeopleDirectory {

	static final PeopleDirectory EMPTY = new PeopleDirectory(List.of());

	/// What a part may take at its end: the endings of the masculine names and surnames in the genitive and accusative,
	/// the dative, the instrumental and the locative.
	private static final String APPENDED = "owi|iem|em|ie|ze|a|u";

	/// What may replace the final "a" of a part: the endings of the feminine names and surnames in the genitive, the
	/// accusative, the instrumental and, with "ie", the dative and the locative.
	private static final String REPLACING = "ie|y|i|ę|ą";

	private static final String WORD_CHARACTER = "[\\p{L}\\p{N}]";

	private static final String LETTER_AND_HYPHEN = "\\p{L}-";

	private final List<Person> people;

	/// The directory of the people with these display names, a name that more than one person has once.
	PeopleDirectory(List<String> displayNames) {
		this.people = new LinkedHashSet<>(displayNames).stream()
				.map(name -> name.strip().split("\\s+"))
				.filter(parts -> parts.length > 1)
				.map(PeopleDirectory::person)
				.toList();
	}

	/// Where the text names a person of the directory, in the order of the text, one person for each place.
	List<Match> find(String text) {
		var candidates = new ArrayList<Candidate>();
		for (var person : people) {
			person.pattern().matcher(text).results().forEach(result -> {
				var match = new Match(person.name(), result.start(), result.end());
				candidates.add(new Candidate(match, result.group().equalsIgnoreCase(person.name())));
			});
		}
		candidates.sort(Comparator.<Candidate>comparingInt(candidate -> candidate.match().start())
				.thenComparingInt(candidate -> candidate.match().end())
				.thenComparing(Candidate::written, Comparator.reverseOrder()));
		var found = new ArrayList<Match>();
		for (var candidate : candidates) {
			var match = candidate.match();
			var last = found.isEmpty() ? null : found.getLast();
			if (last == null || last.start() != match.start() || last.end() != match.end()) {
				found.add(match);
			}
		}
		return found;
	}

	/// `name` as it stands in the directory, and `start` and `end` where the text writes it, `end` exclusive.
	record Match(String name, int start, int end) {
	}

	/// A match, and whether the text writes the name as it stands in the directory, ignoring case.
	private record Candidate(Match match, boolean written) {
	}

	private record Person(String name, Pattern pattern) {
	}

	private static Person person(String[] parts) {
		var regex = new StringBuilder("(?<!" + WORD_CHARACTER + ")(?<!" + LETTER_AND_HYPHEN + ")");
		for (var index = 0; index < parts.length; index++) {
			regex.append(index == 0 ? "" : "\\h+").append(part(parts[index]));
		}
		regex.append("(?!" + WORD_CHARACTER + ")(?!-\\p{L})");
		return new Person(String.join(" ", parts),
				Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
	}

	private static String part(String part) {
		var appended = Pattern.quote(part) + "(?:" + APPENDED + ")?";
		if (part.length() == 1 || Character.toLowerCase(part.charAt(part.length() - 1)) != 'a') {
			return appended;
		}
		return "(?:" + appended + "|" + Pattern.quote(part.substring(0, part.length() - 1)) + "(?:" + REPLACING + "))";
	}

}
