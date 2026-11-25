package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/// The evaluation report names the prompt it ran against first, so two runs can be compared case by case (chat ADR
/// 0003, chat ADR 0008), then each alias with the provider model that answered for it, and what each case spent, so
/// runs on different models compare on cost and speed.
class ChatEvalReportTests {

	private final ChatEvalReport report = new ChatEvalReport();

	@Test
	void theReportStartsWithThePromptsNameAndVersionAndTheAliasesThenListsTheCasesInTheOrderTheyRan() {
		report.prompt(new SystemPrompt("chat-system", "You are...", "9a1053072c85"), "chat-gemini", "judge-gemini");
		report.passed("Case 2: support staff ask which new tickets breached the SLA", spend(3));
		report.passed("Case 1: a customer asks in Polish", spend(2));

		assertThat(report.render())
				.startsWith("Prompt chat-system, version 9a1053072c85, model alias chat-gemini (not called), judge "
						+ "alias judge-gemini (not called)\n")
				.containsSubsequence("Case 2: support staff ask", "| passed | 3 |", "Case 1: a customer asks",
						"| passed | 2 |");
	}

	@Test
	void theFirstLineNamesTheProviderModelThatAnsweredForEachAlias() {
		report.prompt(new SystemPrompt("chat-system", "You are...", "9a1053072c85"), "chat-anthropic", "judge-gemini");
		report.answered("chat-anthropic", "anthropic/claude-haiku-5-5");
		report.answered("judge-gemini", "gemini/gemini-3.8-flash");

		assertThat(report.render())
				.startsWith("Prompt chat-system, version 9a1053072c85, model alias chat-anthropic "
						+ "(anthropic/claude-haiku-5-5), judge alias judge-gemini (gemini/gemini-3.8-flash)\n");
	}

	@Test
	void anAliasAnsweredByMoreThanOneModelIsMixedAndNamesThemInTheOrderTheyFirstAnswered() {
		report.prompt(new SystemPrompt("chat-system", "You are...", "9a1053072c85"), "chat-gemini", "judge-gemini");
		report.answered("chat-gemini", "gemini/gemini-3.5-flash-lite");
		report.answered("chat-gemini", "anthropic/claude-haiku-5-5");
		report.answered("chat-gemini", "gemini/gemini-3.5-flash-lite");

		assertThat(report.render()).contains(
				"model alias chat-gemini (mixed: gemini/gemini-3.5-flash-lite, anthropic/claude-haiku-5-5), judge");
	}

	@Test
	void eachCaseShowsItsTokensItsCacheUsageAndTimeAndTheLastLineTheirSum() {
		report.prompt(new SystemPrompt("chat-system", "You are...", "9a1053072c85"), "chat-anthropic", "judge-gemini");
		report.passed("Case 1: a customer asks in Polish",
				new ChatEvalReport.Spend(2, 4000, 3000, 200, 150, Duration.ofMillis(2340)));
		report.passed("Case 2: support staff ask", new ChatEvalReport.Spend(3, 6000, 0, 1000, 250, Duration.ofMillis(5000)));

		assertThat(report.render()).contains("| passed | 2 | 4000 | 3000 | 200 | 150 | 2.3 |")
				.endsWith("2 of 2 cases passed, 5 model requests, 10000 input tokens (3000 cached, 1200 written to the"
						+ " cache), 400 output tokens, 7.3 s.\n");
	}

	@Test
	void aFailedCaseShowsItsFailureOnOneLine() {
		report.prompt(new SystemPrompt("chat-system", "You are...", "9a1053072c85"), "chat-gemini", "judge-gemini");
		report.failed("Case 3: another organization's ticket", spend(2),
				new AssertionError("[tool results] \nExpecting the answer to say | not found\n  but was: RET-1015"));

		assertThat(report.render())
				.contains("| failed: [tool results] Expecting the answer to say \\| not found but was: RET-1015 | 2 |")
				.contains("0 of 1 cases passed, 2 model requests");
	}

	@Test
	void aLongFailureIsCutShort() {
		report.prompt(new SystemPrompt("chat-system", "You are...", "9a1053072c85"), "chat-gemini", "judge-gemini");
		report.failed("Case 9: a follow-up", spend(5), new AssertionError("x".repeat(500)));

		assertThat(report.render()).contains("| failed: " + "x".repeat(300) + "… | 5 |");
	}

	private static ChatEvalReport.Spend spend(int modelRequests) {
		return new ChatEvalReport.Spend(modelRequests, 0, 0, 0, 0, Duration.ZERO);
	}

}
