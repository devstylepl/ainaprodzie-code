package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

/// [ToolCallEventsAdvisor] reports tool calls to the turn in the request's tool context, and refuses a request without
/// one.
class ToolCallEventsAdvisorTests {

	private static final String QUESTION = "Which tickets do we have?";

	private final ToolCallEventsAdvisor advisor = new ToolCallEventsAdvisor(JsonMapper.builder().build());

	@Test
	void aRequestWithoutToolContextIsRefused() {
		var request = ChatClientRequest.builder().prompt(new Prompt(QUESTION)).build();

		assertThatIllegalStateException().isThrownBy(() -> advisor.adviseStream(request, new EmptyChain()));
	}

	@Test
	void aToolContextWithoutATurnIsRefused() {
		var options = ToolCallingChatOptions.builder().toolContext(Map.of("locale", "en")).build();
		var request = ChatClientRequest.builder().prompt(new Prompt(QUESTION, options)).build();

		assertThatIllegalStateException().isThrownBy(() -> advisor.adviseStream(request, new EmptyChain()));
	}

	private static final class EmptyChain implements StreamAdvisorChain {

		@Override
		public Flux<ChatClientResponse> nextStream(ChatClientRequest request) {
			return Flux.empty();
		}

		@Override
		public List<StreamAdvisor> getStreamAdvisors() {
			return List.of();
		}

		@Override
		public StreamAdvisorChain copy(StreamAdvisor after) {
			return this;
		}

	}

}
