package app.retrofit.chat;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

/// An LLM judge for a quality a direct assertion cannot check, such as "declines politely" (chat ADR 0003). It asks
/// the model of the client it is given, the judge's alias in the evaluations, whether the answer meets `criterion`,
/// without the chat's system prompt or tools. Only an explicit `PASS` passes: a verdict it cannot read fails the case,
/// and the feedback quotes it.
final class ChatJudge implements Evaluator {

	private static final String PROMPT = """
			You grade one answer of the support assistant of a help desk. The assistant can only read the tickets \
			the signed-in user may see.

			<question>
			%s
			</question>

			<answer>
			%s
			</answer>

			Criterion: %s

			Does the answer meet the criterion? Reply with PASS or FAIL on the first line, then one sentence that \
			says why.""";

	private final ChatClient chatClient;

	private final String criterion;

	ChatJudge(ChatClient.Builder builder, String criterion) {
		this.chatClient = builder.build();
		this.criterion = criterion;
	}

	/// `request`'s user text is the question, its response content the answer to grade.
	@Override
	public EvaluationResponse evaluate(EvaluationRequest request) {
		var reply = chatClient.prompt()
				.user(PROMPT.formatted(request.getUserText(), request.getResponseContent(), criterion))
				.stream()
				.content()
				.collect(Collectors.joining())
				.block();
		var verdict = reply == null ? "" : reply.strip();
		var firstWord = verdict.replaceFirst("^[^\\p{L}]+", "").split("[^\\p{L}]", 2)[0].toUpperCase(Locale.ROOT);
		return new EvaluationResponse("PASS".equals(firstWord), "Judge: " + verdict, Map.of());
	}

}
