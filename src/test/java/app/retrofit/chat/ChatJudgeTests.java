package app.retrofit.chat;

import static app.retrofit.chat.ScriptedChatModel.answer;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.EvaluationRequest;

import io.micrometer.observation.ObservationRegistry;

/// The judge of the evaluations reads the model's verdict strictly: only an explicit `PASS` passes.
class ChatJudgeTests {

	private static final String QUESTION = "Write a poem about cats";

	private static final String ANSWER = "I can only help with your tickets.";

	private final ScriptedChatModel model = new ScriptedChatModel(ObservationRegistry.NOOP);

	private final ChatJudge judge = new ChatJudge(ChatClient.builder(model), "The assistant declines politely.");

	@Test
	void aVerdictOfPassPassesWithTheReasonAsFeedback() {
		model.script(answer("PASS\nIt declines and offers help with tickets."));

		var verdict = judge.evaluate(new EvaluationRequest(QUESTION, List.of(), ANSWER));

		assertThat(verdict.isPass()).isTrue();
		assertThat(verdict.getFeedback()).contains("It declines and offers help with tickets.");
	}

	@Test
	void aVerdictInMarkdownWithPunctuationIsStillRead() {
		model.script(answer("**Pass.** It declines."));

		assertThat(judge.evaluate(new EvaluationRequest(QUESTION, List.of(), ANSWER)).isPass()).isTrue();
	}

	@Test
	void aVerdictOfFailFails() {
		model.script(answer("FAIL\nIt wrote the poem."));

		var verdict = judge.evaluate(new EvaluationRequest(QUESTION, List.of(), ANSWER));

		assertThat(verdict.isPass()).isFalse();
		assertThat(verdict.getFeedback()).contains("It wrote the poem.");
	}

	@Test
	void anUnreadableVerdictFailsRatherThanPasses() {
		model.script(answer("The answer seems mostly fine."));

		var verdict = judge.evaluate(new EvaluationRequest(QUESTION, List.of(), ANSWER));

		assertThat(verdict.isPass()).isFalse();
		assertThat(verdict.getFeedback()).contains("The answer seems mostly fine.");
	}

	@Test
	void theJudgeReadsTheQuestionTheAnswerAndTheCriterion() {
		model.script(answer("PASS"));

		judge.evaluate(new EvaluationRequest(QUESTION, List.of(), ANSWER));

		assertThat(model.prompts().getFirst().getContents()).contains(QUESTION, ANSWER,
				"The assistant declines politely.");
	}

}
