package app.retrofit.chat;

import static app.retrofit.chat.LiteLlmConfig.alias;
import static app.retrofit.chat.LiteLlmConfig.appModels;
import static app.retrofit.chat.LiteLlmConfig.parameterList;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import tools.jackson.databind.json.JsonMapper;

/// LiteLLM's Presidio guardrail is the only layer for e-mail addresses, phone numbers, IBANs and PESELs (chat ADR
/// 0017): it masks them before a request reaches the provider. An alias without it sends them as they are, a guardrail
/// that looks for other data than the app's recognizers find finds what the app was never told to find, and an
/// analyzer without the PESEL recognizer for English finds no PESEL. The same aliases keep the caller's id from the
/// provider (chat ADR 0015).
class PersonalDataGuardrailTests {

	/// The guardrail of `litellm/config.yaml`.
	static final String GUARDRAIL = "personal-data";

	/// What an alias of the app drops before the provider (chat ADR 0015).
	private static final String USER = "user";

	/// Presidio takes words for people that are no name, and the guardrail cannot be given the app's allow-list.
	private static final String PERSON = "PERSON";

	@Test
	void everyAliasTheAppCallsHasTheGuardrail() {
		assertThat(appModels()).isNotEmpty()
			.allSatisfy(model -> assertThat(parameterList(model, "guardrails")).as(alias(model)).contains(GUARDRAIL));
	}

	/// LiteLLM would pass the request's `user`, the caller's id, to Anthropic as `metadata.user_id`.
	@Test
	void everyAliasTheAppCallsDropsTheUserBeforeTheProvider() {
		assertThat(appModels()).isNotEmpty()
			.allSatisfy(model -> assertThat(parameterList(model, "additional_drop_params")).as(alias(model))
				.contains(USER));
	}

	@Test
	@SuppressWarnings("unchecked")
	void theGuardrailMasksWhatTheAppFindsButPeopleAndBlocksCardNumbers() throws IOException {
		var entities = new ArrayList<>((List<String>) analyzer().get("entities"));
		entities.remove(PERSON);
		var config = (Map<String, String>) guardrailParameters().get("pii_entities_config");

		assertThat(config.keySet()).containsExactlyInAnyOrderElementsOf(entities);
		assertThat(config).allSatisfy((entity, action) -> assertThat(action).as(entity)
			.isEqualTo(entity.equals("CREDIT_CARD") ? "BLOCK" : "MASK"));
	}

	@Test
	void theGuardrailHasTheAppsRecognizers() throws IOException {
		var file = Path.of("litellm", Path.of((String) guardrailParameters().get("presidio_ad_hoc_recognizers"))
			.getFileName()
			.toString());

		assertThat(JsonMapper.builder().build().readValue(Files.readString(file), List.class))
			.isEqualTo(analyzer().get("ad_hoc_recognizers"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void theAnalyzerFindsAPeselInEnglish() throws IOException {
		Map<String, Object> registry = new Yaml().load(Files.readString(Path.of("presidio", "recognizers.yaml")));

		assertThat((List<Map<String, Object>>) registry.get("recognizers"))
			.filteredOn(recognizer -> "PlPeselRecognizer".equals(recognizer.get("name")))
			.singleElement()
			.satisfies(recognizer -> assertThat(recognizer.get("supported_languages")).isEqualTo(List.of("en")));
	}

	/// LiteLLM's own `presidio` refuses a question that holds some IBANs: Presidio finds a card number inside them.
	@Test
	void theGuardrailIsTheClassThatTakesNoPartOfAnIbanForACardNumber() throws IOException {
		assertThat(guardrailParameters().get("guardrail")).isEqualTo("personal_data_guardrail.PersonalDataGuardrail");
	}

	/// LiteLLM loads the class from a file next to its `config.yaml`, and does not start without it.
	@Test
	@SuppressWarnings("unchecked")
	void composeMountsTheGuardrailsClassNextToLiteLlmsConfig() throws IOException {
		Map<String, Object> compose = new Yaml().load(Files.readString(Path.of("compose.yaml")));
		var litellm = ((Map<String, Map<String, Object>>) compose.get("services")).get("litellm");

		assertThat(Path.of("litellm", "personal_data_guardrail.py")).isRegularFile();
		assertThat((List<String>) litellm.get("volumes"))
			.contains("./litellm/personal_data_guardrail.py:/app/personal_data_guardrail.py:ro");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> guardrailParameters() throws IOException {
		var guardrails = (List<Map<String, Object>>) LiteLlmConfig.read().getOrDefault("guardrails", List.of());
		return guardrails.stream()
			.filter(guardrail -> GUARDRAIL.equals(guardrail.get("guardrail_name")))
			.map(guardrail -> (Map<String, Object>) guardrail.get("litellm_params"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("litellm/config.yaml has no guardrail " + GUARDRAIL));
	}

	private static Map<String, Object> analyzer() {
		return PresidioRequests.read(new ClassPathResource("presidio/requests.yaml")).analyzer();
	}

}
