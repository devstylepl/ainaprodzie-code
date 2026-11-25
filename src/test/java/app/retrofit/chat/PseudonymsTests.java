package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/// The placeholders of one conversation (chat ADR 0009), on texts small enough to count the offsets by hand.
class PseudonymsTests {

	private final Pseudonyms pseudonyms = new Pseudonyms();

	private final PeopleDirectory directory = new PeopleDirectory(List.of("Adam Lee", "Bob Ray"));

	@Test
	void aPersonOfTheDirectoryIsNumberedOnceWhateverTheCaseOfTheirName() {
		var pseudonymized = pseudonyms.pseudonymize("Adam Lee, Bob Ray, Adama Lee, Adam Lee", directory);

		assertThat(pseudonymized).isEqualTo("<PERSON_1>, <PERSON_2>, <PERSON_1>, <PERSON_1>");
	}

	/// A tool result's field names the person as the directory does, so both give one placeholder.
	@Test
	void aPersonOfTheDirectoryAndTheSameNameFromAFieldShareAPlaceholder() {
		pseudonyms.person("Bob Ray");

		assertThat(pseudonyms.pseudonymize("Bob Ray and Boba Raya", directory)).isEqualTo("<PERSON_1> and <PERSON_1>");
		assertThat(pseudonyms.restore("<PERSON_1>")).isEqualTo("Bob Ray");
	}

	@Test
	void theUserReadsTheDirectoryNameWhereTheQuestionHadAnotherCaseOfIt() {
		var pseudonymized = pseudonyms.pseudonymize("Czy to Adama Lee?", directory);

		assertThat(pseudonymized).isEqualTo("Czy to <PERSON_1>?");
		assertThat(pseudonyms.restore(pseudonymized)).isEqualTo("Czy to Adam Lee?");
	}

	/// An address is no person here: the gateway masks it (chat ADR 0017).
	@Test
	void anEmailAddressIsLeftAsWritten() {
		assertThat(pseudonyms.pseudonymize("Mail adam.lee@x.example", directory)).isEqualTo("Mail adam.lee@x.example");
	}

	@Test
	void aValueTheConversationKnowsIsReplacedWhereverItStandsAsAWholeWord() {
		pseudonyms.person("Al");

		assertThat(pseudonyms.pseudonymize("Also ask Al, then Alan.", PeopleDirectory.EMPTY)).isEqualTo(
				"Also ask <PERSON_1>, then Alan.");
	}

	/// A name that is a part of a longer name of the directory is the longer name where they start together.
	@Test
	void ofSpansThatStartTogetherTheLongerIsKept() {
		pseudonyms.person("Adam");

		assertThat(pseudonyms.pseudonymize("Adam Lee, Adam", directory)).isEqualTo("<PERSON_2>, <PERSON_1>");
	}

	/// An empty name as a known value would match at every position and never stop.
	@Test
	void aBlankNameGetsNoPlaceholder() {
		pseudonyms.person("");
		pseudonyms.person(" ");

		assertThat(pseudonyms.pseudonymize("Ask  Ann", PeopleDirectory.EMPTY)).isEqualTo("Ask  Ann");
	}

	@Test
	void aPlaceholderTheConversationNeverGaveOutStaysAsItIs() {
		pseudonyms.person("Ann");

		assertThat(pseudonyms.restore("<PERSON_1> and <PERSON_2>")).isEqualTo("Ann and <PERSON_2>");
	}

	/// Read before the conversation gives `<PERSON_1>` out, the text still never becomes that person.
	@Test
	void aTextThatLooksLikeAPlaceholderIsRestoredAsItIs() {
		var quoted = pseudonyms.pseudonymize("Log: <PERSON_1>, <PERSON\\_1>, <PLACEHOLDER_1>", PeopleDirectory.EMPTY);
		pseudonyms.person("Ann");

		assertThat(quoted).isEqualTo("Log: <PLACEHOLDER_1>, <PLACEHOLDER_2>, <PLACEHOLDER_3>");
		assertThat(pseudonyms.restore(quoted + " <PERSON_1>"))
				.isEqualTo("Log: <PERSON_1>, <PERSON\\_1>, <PLACEHOLDER_1> Ann");
	}

	@Test
	void onlyAnEndThatCanStillBecomeAPlaceholderIsUnfinished() {
		assertThat(Pseudonyms.unfinishedPlaceholderStart("Ask <PER", 0)).isEqualTo(4);
		assertThat(Pseudonyms.unfinishedPlaceholderStart("Ask <PERSON\\", 0)).isEqualTo(4);
		assertThat(Pseudonyms.unfinishedPlaceholderStart("Ask <", 0)).isEqualTo(4);
		assertThat(Pseudonyms.unfinishedPlaceholderStart("1 < 2", 0)).isEqualTo(5);
		assertThat(Pseudonyms.unfinishedPlaceholderStart("Ask <PERSON_1>", 0)).isEqualTo(14);
	}

}
