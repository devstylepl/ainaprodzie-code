package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;

import org.junit.jupiter.api.Test;

/// The evaluations hand LiteLLM the key of the alias the chat names, so they run on whichever provider a participant
/// brings (chat ADR 0003).
class ChatEvalConfigurationTests {

	private static final String CONFIG = """
			model_list:
			  - model_name: chat-gemini
			    litellm_params:
			      model: gemini/gemini-3.5-flash-lite
			      api_key: os.environ/GEMINI_API_KEY
			  - model_name: chat-anthropic
			    litellm_params:
			      model: anthropic/claude-haiku-5-5
			      api_key: os.environ/ANTHROPIC_API_KEY
			  - model_name: chat-local
			    litellm_params:
			      model: ollama_chat/qwen3:8b
			      api_base: http://host.docker.internal:11434
			  - model_name: chat-bedrock
			    litellm_params:
			      model: bedrock/anthropic.claude-haiku-5-5
			      aws_access_key_id: os.environ/AWS_ACCESS_KEY_ID
			      aws_secret_access_key: os.environ/AWS_SECRET_ACCESS_KEY
			  - model_name: chat-bedrock
			    litellm_params:
			      model: bedrock/anthropic.claude-haiku-5-5
			      aws_access_key_id: os.environ/AWS_ACCESS_KEY_ID
			      aws_secret_access_key: os.environ/AWS_SECRET_ACCESS_KEY
			      aws_session_token: os.environ/AWS_SESSION_TOKEN
			  - model_name: judge-gemini
			    litellm_params:
			      model: gemini/gemini-3.8-flash
			      api_key: os.environ/GEMINI_API_KEY
			  - model_name: judge-gemini
			    litellm_params:
			      model: gemini/gemini-3.5-flash
			      api_key: os.environ/GEMINI_API_KEY
			""";

	@Test
	void anAliasNeedsTheVariableItsEntryReadsTheKeyFrom() {
		assertThat(ChatEvalConfiguration.providerKeyVariables(CONFIG, "chat-gemini")).containsExactly("GEMINI_API_KEY");
		assertThat(ChatEvalConfiguration.providerKeyVariables(CONFIG, "chat-anthropic"))
				.containsExactly("ANTHROPIC_API_KEY");
	}

	@Test
	void anAliasNeedsEveryVariableOfEveryEntry() {
		assertThat(ChatEvalConfiguration.providerKeyVariables(CONFIG, "chat-bedrock"))
				.containsExactly("AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN");
	}

	@Test
	void anAliasAnsweredByTheModelItsEntryNamesHasNothingToReport() {
		assertThat(ChatEvalConfiguration.foreignAnswer(CONFIG, "chat-gemini", List.of("gemini/gemini-3.5-flash-lite")))
				.isNull();
	}

	@Test
	void anAliasAnsweredByAModelItsEntryDoesNotNameIsReportedWithBothNames() {
		assertThat(ChatEvalConfiguration.foreignAnswer(CONFIG, "chat-gemini", List.of("anthropic/claude-haiku-5-5")))
				.isEqualTo("chat-gemini was answered by anthropic/claude-haiku-5-5; litellm/config.yaml names "
						+ "gemini/gemini-3.5-flash-lite for it");
	}

	@Test
	void anAliasWithTwoEntriesAnsweredByBothHasNothingToReport() {
		assertThat(ChatEvalConfiguration.foreignAnswer(CONFIG, "judge-gemini",
				List.of("gemini/gemini-3.8-flash", "gemini/gemini-3.5-flash"))).isNull();
	}

	@Test
	void aLocalModelNeedsNoKey() {
		assertThat(ChatEvalConfiguration.providerKeyVariables(CONFIG, "chat-local")).isEmpty();
	}

	@Test
	void anAliasTheConfigDoesNotListFailsTheRunNamingIt() {
		assertThatIllegalStateException()
				.isThrownBy(() -> ChatEvalConfiguration.providerKeyVariables(CONFIG, "chat-openai"))
				.withMessageContaining("chat-openai");
	}

	@Test
	void aConfigWithEveryModelCommentedOutFailsTheRunNamingTheAlias() {
		assertThatIllegalStateException()
				.isThrownBy(() -> ChatEvalConfiguration.providerKeyVariables("model_list:\n", "chat-gemini"))
				.withMessageContaining("chat-gemini");
	}

}
