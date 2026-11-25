package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.json.JsonMapper;

/// What Presidio's analyzer, a model of its own, finds in the texts of the app's spans, and what its anonymizer leaves
/// of them (chat ADR 0007), with the images of compose.yaml. It runs with the evaluations (chat ADR 0003), though no
/// chat model takes part.
@Tag("eval")
@Testcontainers
class PresidioMaskerEval {

	@Container
	static final GenericContainer<?> ANALYZER = ChatEvalConfiguration.presidioAnalyzer();

	@Container
	static final GenericContainer<?> ANONYMIZER = ChatEvalConfiguration
			.presidio(ChatEvalConfiguration.PRESIDIO_ANONYMIZER_IMAGE);

	private final PresidioMasker presidio = new PresidioMasker(url(ANALYZER), url(ANONYMIZER),
			PresidioRequests.read(new ClassPathResource("presidio/requests.yaml")), JsonMapper.builder().build());

	/// Presidio took `RET-1047` for a person here, so a span showed `<PERSON>` for a reference.
	@Test
	void aTicketReferenceWhereANameCouldStandIsNotAPerson() {
		var text = "Add a note for my colleagues on RET-1047: the fix ships with Friday's release";

		assertThat(presidio.mask(text)).isEqualTo(text);
	}

	/// The product the tickets are about stands where a name could, and Presidio took it for a person.
	@Test
	void theProductIsNotAPerson() {
		assertThat(presidio.mask("We are moving 3 Jira projects to Taskline"))
				.isEqualTo("We are moving 3 Jira projects to Taskline");
		assertThat(presidio.mask("Taskline rejected SAML assertions issued more than 30 seconds in the past"))
				.isEqualTo("Taskline rejected SAML assertions issued more than 30 seconds in the past");
	}

	@Test
	void aSystemTheCustomersConnectIsNotAPerson() {
		var question = "Could you check whether the signing certificate in your Okta application was renewed?";

		assertThat(presidio.mask(question)).isEqualTo(question);
		assertThat(presidio.mask("Pointed to Import > Jira Cloud")).isEqualTo("Pointed to Import > Jira Cloud");
	}

	@Test
	void aPersonNextToATicketReferenceIsStillMasked() {
		assertThat(presidio.mask("Ask Jan Kowalski on RET-1048 about the export"))
				.isEqualTo("Ask <PERSON> on RET-1048 about the export");
	}

	/// Presidio's own PESEL recognizer loads only for Polish by default, and the app asks in English, so
	/// `presidio/recognizers.yaml` lists it for English (chat ADR 0017).
	@Test
	void aPolishPeselIsMaskedAsOneFinding() {
		var text = "For the direct debit mandate the bank needs the signatory: Irene Walsh, PESEL 85071801246";

		assertThat(presidio.mask(text)).endsWith("PESEL <PL_PESEL>");
	}

	private static URI url(GenericContainer<?> container) {
		return URI.create(ChatEvalConfiguration.url(container, ChatEvalConfiguration.PRESIDIO_PORT));
	}

}
