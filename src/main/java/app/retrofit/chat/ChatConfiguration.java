package app.retrofit.chat;

import java.util.Arrays;
import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.EnableScheduling;

import tools.jackson.databind.json.JsonMapper;

/// The chat's one `ChatClient`, built from the builder Spring AI configures for the model in `application.yml`, with
/// the system prompt, the read tools and the tool call events; its memory; and what the model reads when a tool fails.
///
/// The tool-call limit is `spring.ai.tools.limits` in `application.yml`, which Spring AI applies to the
/// `ToolCallingManager` it builds.
///
/// Scheduling is enabled here, by the feature that needs it: [ChatService] forgets idle conversations on a schedule
/// (chat ADR 0005).
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ChatConfiguration {

	private static final Logger log = LoggerFactory.getLogger(ChatConfiguration.class);

	/// What the model reads instead of a tool's result when the tool fails: neutral, so the model says it could not
	/// read the tickets rather than guessing at a cause.
	static final String TOOL_FAILURE = "Could not read the tickets right now.";

	/// The messages a conversation remembers, its latest ten questions and answers (chat ADR 0005).
	static final int MEMORY_WINDOW = 20;

	@Bean
	SystemPrompt systemPrompt(@Value("classpath:prompts/chat-system.st") Resource resource) {
		return SystemPrompt.read("chat-system", resource);
	}

	/// The tools go in a fixed order, sorted by name, so every request's prefix is the same (chat ADR 0008), and the
	/// model reads their results pseudonymized (chat ADR 0009).
	@Bean
	ChatClient chatClient(ChatClient.Builder builder, SystemPrompt systemPrompt, TicketReadTools ticketReadTools,
			JsonMapper json) {
		var tools = Arrays.stream(MethodToolCallbackProvider.builder().toolObjects(ticketReadTools).build()
				.getToolCallbacks())
				.sorted(Comparator.comparing(tool -> tool.getToolDefinition().name()))
				.map(tool -> ChatTurn.tool(tool, json))
				.toArray(ToolCallback[]::new);
		return builder.defaultSystem(systemPrompt.text())
				.defaultToolCallbacks(tools)
				.defaultAdvisors(new ToolCallEventsAdvisor(json))
				.build();
	}

	@Bean
	ChatMemory chatMemory() {
		return MessageWindowChatMemory.builder()
				.chatMemoryRepository(new InMemoryChatMemoryRepository())
				.maxMessages(MEMORY_WINDOW)
				.build();
	}

	/// A tool's exception never reaches the model or the user: it is logged with the tool's name and its cause as a
	/// [RedactedFailure], never with the tool's arguments or result, and the model reads [#TOOL_FAILURE].
	@Bean
	ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
		return exception -> {
			log.warn("Tool {} failed", exception.getToolDefinition().name(), RedactedFailure.of(exception.getCause()));
			return TOOL_FAILURE;
		};
	}

}
