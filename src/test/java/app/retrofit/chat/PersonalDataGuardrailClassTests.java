package app.retrofit.chat;

import org.junit.jupiter.api.Test;

/// The rule of `litellm/personal_data_guardrail.py`, that a card number Presidio finds inside an IBAN does not refuse a
/// request (chat ADR 0017), checked by `litellm/test_personal_data_guardrail.py` in the LiteLLM image `compose.yaml`
/// runs. The Python test goes through LiteLLM's own `check_pii`, so it also fails when a new LiteLLM stops calling the
/// method the class overrides.
class PersonalDataGuardrailClassTests {

	@Test
	void aCardNumberInsideAnIbanDoesNotRefuseTheRequestAndOneElsewhereDoes() {
		LiteLlmPython.run("test_personal_data_guardrail", "personal_data_guardrail");
	}

}
