package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import app.retrofit.chat.PeopleDirectory.Match;

/// Which texts name a person of the directory (chat ADR 0009): the full display name, in any case of letters and in a
/// Polish case, on texts small enough to count the offsets by hand.
class PeopleDirectoryTests {

	/// The display names of the accounts of the demo data, in the order it creates them.
	private static final List<String> SEED = seedNames();

	/// A name in the five cases a Polish sentence puts it in: genitive, dative, accusative, instrumental and locative,
	/// as a Polish speaker writes it; a name that does not decline is the same five times.
	private record Declension(String name, String genitive, String dative, String accusative, String instrumental,
			String locative) {

		Stream<Arguments> cases() {
			return Stream.of(Arguments.of(name, "genitive", genitive), Arguments.of(name, "dative", dative),
					Arguments.of(name, "accusative", accusative), Arguments.of(name, "instrumental", instrumental),
					Arguments.of(name, "locative", locative));
		}

		static Declension indeclinable(String name) {
			return new Declension(name, name, name, name, name, name);
		}

	}

	private static final List<Declension> SEED_DECLENSIONS = List.of(
			new Declension("Una User", "Uny Usera", "Unie Userowi", "Unę Usera", "Uną Userem", "Unie Userze"),
			new Declension("Sam Support", "Sama Supporta", "Samowi Supportowi", "Sama Supporta",
					"Samem Supportem", "Samie Supporcie"),
			new Declension("Adam Novak", "Adama Novaka", "Adamowi Novakowi", "Adama Novaka", "Adamem Novakiem",
					"Adamie Novaku"),
			Declension.indeclinable("Alice Moreau"),
			new Declension("Greta Lind", "Grety Lind", "Grecie Lind", "Gretę Lind", "Gretą Lind", "Grecie Lind"),
			new Declension("Gil Ortega", "Gila Ortegi", "Gilowi Ortedze", "Gila Ortegę", "Gilem Ortegą",
					"Gilu Ortedze"),
			new Declension("Ian Chu", "Iana Chu", "Ianowi Chu", "Iana Chu", "Ianem Chu", "Ianie Chu"),
			Declension.indeclinable("Irene Walsh"), Declension.indeclinable("Ivy Ingram"),
			new Declension("Ben Barker", "Bena Barkera", "Benowi Barkerowi", "Bena Barkera", "Benem Barkerem",
					"Benie Barkerze"));

	/// The forms that change the stem, which the directory does not match: not protected (chat ADR 0009).
	private static final Set<String> MISSED = Set.of("Samie Supporcie", "Grecie Lind", "Gilowi Ortedze",
			"Gilu Ortedze");

	private final PeopleDirectory directory = new PeopleDirectory(SEED);

	private static List<String> seedNames() {
		try {
			var account = Pattern.compile("'[0-9a-f-]+', '[^']+@[^']+', '([^']+)', '(?:USER|SUPPORT)'");
			return account.matcher(Files.readString(Path.of("src", "main", "resources", "db", "seed", "demo-data.sql")))
					.results()
					.map(match -> match.group(1))
					.toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static Stream<Arguments> foundForms() {
		return SEED_DECLENSIONS.stream().flatMap(Declension::cases).filter(row -> !MISSED.contains(row.get()[2]));
	}

	static Stream<Arguments> missedForms() {
		return SEED_DECLENSIONS.stream().flatMap(Declension::cases).filter(row -> MISSED.contains(row.get()[2]));
	}

	@ParameterizedTest(name = "{0} in the {1}: {2}")
	@MethodSource("foundForms")
	void aSeedNameIsFoundInTheCaseFormsItsEndingsMake(String name, String grammaticalCase, String form) {
		assertThat(directory.find("Czy to " + form + " dotyczy?")).containsExactly(new Match(name, 7,
				7 + form.length()));
	}

	@ParameterizedTest(name = "{0} in the {1}: {2}")
	@MethodSource("missedForms")
	void aSeedNameIsMissedInAFormThatChangesTheStem(String name, String grammaticalCase, String form) {
		assertThat(directory.find("Czy to " + form + " dotyczy?")).isEmpty();
	}

	@Test
	void theTableHoldsEveryNameOfTheSeed() {
		assertThat(SEED).hasSize(10);
		assertThat(SEED_DECLENSIONS).extracting(Declension::name).containsExactlyElementsOf(SEED);
	}

	@Test
	void aMatchNamesThePersonAndWhereTheTextIs() {
		assertThat(directory.find("Czy to Bena Barkera?")).containsExactly(new Match("Ben Barker", 7, 19));
	}

	@Test
	void everyMatchOfATextIsFound() {
		assertThat(directory.find("Ben Barker i Adamowi Novakowi, Ben Barker")).containsExactlyInAnyOrder(
				new Match("Ben Barker", 0, 10), new Match("Adam Novak", 13, 29), new Match("Ben Barker", 31, 41));
	}

	@Test
	void theCaseOfTheLettersDoesNotMatter() {
		assertThat(directory.find("BEN barker, adama NOVAKA")).extracting(Match::name)
				.containsExactly("Ben Barker", "Adam Novak");
	}

	@Test
	void everyPartOfTheNameIsNeededInOrderSideBySide() {
		assertThat(directory.find("Ben, Barker, Barker Ben, Ben T. Barker, Ben i Barker, Ben\nBarker")).isEmpty();
		assertThat(directory.find("Ben  Barker")).hasSize(1);
	}

	@Test
	void onePartOfANameAloneIsNoOne() {
		assertThat(directory.find("Alice, Sam, Ben and Support")).isEmpty();
	}

	@Test
	void aDisplayNameOfOneWordIsNoOne() {
		assertThat(new PeopleDirectory(List.of("Madonna", "Sam")).find("Madonna, Madonny and Sam")).isEmpty();
	}

	@Test
	void aNameInsideAWordIsNoOne() {
		assertThat(directory.find("XBen Barker, Ben Barkerowie, Ben Barker2")).isEmpty();
	}

	@Test
	void aPartWithAHyphenIsOnePartWithTheEndingAtItsEnd() {
		var hyphenated = new PeopleDirectory(List.of("Jean-Luc Picard"));

		assertThat(hyphenated.find("Jean-Luca Picarda, Jean-Luc Picard")).extracting(Match::name)
				.containsExactly("Jean-Luc Picard", "Jean-Luc Picard");
		assertThat(hyphenated.find("Jean Luc Picard")).isEmpty();
	}

	@Test
	void aHyphenAndALetterAfterTheNameMakeItAnotherPersonsName() {
		var directory = new PeopleDirectory(List.of("Ann Lee"));

		assertThat(directory.find("Ann Lee-Smith wrote, Ann Leea-Smith too")).isEmpty();
		assertThat(directory.find("Ann Lee - Smith wrote, Ann Lee-, Ann Lee- 5")).extracting(Match::name)
				.containsExactly("Ann Lee", "Ann Lee", "Ann Lee");
	}

	@Test
	void aLetterAndAHyphenBeforeTheNameMakeItAnotherPersonsName() {
		var directory = new PeopleDirectory(List.of("Ann Lee"));

		assertThat(directory.find("Smith-Ann Lee wrote")).isEmpty();
		assertThat(directory.find("Smith - Ann Lee, -Ann Lee, 5-Ann Lee")).extracting(Match::name)
				.containsExactly("Ann Lee", "Ann Lee", "Ann Lee");
	}

	@Test
	void theNameAsWrittenWinsOverACaseFormOfAnotherName() {
		var directory = new PeopleDirectory(List.of("Sam Support", "Sam Supporta"));

		assertThat(directory.find("Czy to Sam Supporta?")).containsExactly(new Match("Sam Supporta", 7, 19));
		assertThat(directory.find("Czy to sam SUPPORTA?")).containsExactly(new Match("Sam Supporta", 7, 19));
		assertThat(directory.find("Czy to Sama Supporta?")).containsExactly(new Match("Sam Support", 7, 20));
	}

	@Test
	void theWinningNameDoesNotDependOnTheOrderOfTheDirectory() {
		var directory = new PeopleDirectory(List.of("Sam Supporta", "Sam Support"));

		assertThat(directory.find("Czy to Sam Supporta?")).containsExactly(new Match("Sam Supporta", 7, 19));
	}

	@Test
	void anEndingDoesNotReplaceAPartOfOneLetter() {
		var directory = new PeopleDirectory(List.of("Jan A Kowalski"));

		assertThat(directory.find("Czy Jan i Kowalski, Jan y Kowalski, Jan ą Kowalski?")).isEmpty();
		assertThat(directory.find("Czy Jan A Kowalski?")).containsExactly(new Match("Jan A Kowalski", 4, 18));
	}

	@Test
	void aNameThatIsADirectoryNamePlusAnEndingIsThatPerson() {
		var directory = new PeopleDirectory(List.of("Ann Lee"));

		assertThat(directory.find("Anna Lee wrote")).containsExactly(new Match("Ann Lee", 0, 8));
	}

	@Test
	void aDirectoryNobodyIsInFindsNoOne() {
		assertThat(PeopleDirectory.EMPTY.find("Ben Barker")).isEmpty();
	}

}
