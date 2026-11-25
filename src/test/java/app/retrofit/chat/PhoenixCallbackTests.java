package app.retrofit.chat;

import org.junit.jupiter.api.Test;

/// The user and the session `litellm/phoenix_callback.py` puts on LiteLLM's spans (chat ADR 0015), checked by
/// `litellm/test_phoenix_callback.py` in the LiteLLM image `compose.yaml` runs, so a LiteLLM that renames what the
/// callback reads turns the build red.
class PhoenixCallbackTests {

	@Test
	void theCallerIsTheSpansUserAndTheConversationItsSession() {
		LiteLlmPython.run("test_phoenix_callback", "phoenix_callback");
	}

}
