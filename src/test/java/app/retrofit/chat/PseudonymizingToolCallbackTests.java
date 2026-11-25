package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import app.retrofit.organization.Caller;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.json.JsonMapper;

/// What the model reads of a tool's result (chat ADR 0009), with a directory nobody is in, so only the people the
/// result names in its own fields become placeholders.
class PseudonymizingToolCallbackTests {

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void theAssigneesOfAnAssigneeChangeArePersonsAndAnotherChangesValuesAreNot() {
		var result = """
				{"changes":[
				{"actor":"Ann Lee","field":"ASSIGNEE","oldValue":"Bob Ray","newValue":"Cy Moe"},
				{"actor":"Ann Lee","field":"TITLE","oldValue":"Printer","newValue":"Scanner"}]}""";
		var turn = new ChatTurn(new Caller.Support(UUID.randomUUID()), new Pseudonyms(), PeopleDirectory.EMPTY, true,
				Sinks.many().unicast().onBackpressureBuffer());

		var read = tool(result).call("{}", new ToolContext(turn.toolContext()));

		assertThat(read).doesNotContain("Ann Lee", "Bob Ray", "Cy Moe")
				.contains("\"oldValue\":\"<PERSON_2>\"", "\"newValue\":\"<PERSON_3>\"", "\"oldValue\":\"Printer\"");
	}

	private PseudonymizingToolCallback tool(String result) {
		var definition = ToolDefinition.builder().name("getTicket").description("A ticket").inputSchema("{}").build();
		ToolCallback tool = new ToolCallback() {

			@Override
			public ToolDefinition getToolDefinition() {
				return definition;
			}

			@Override
			public String call(String toolInput) {
				return result;
			}

			@Override
			public String call(String toolInput, ToolContext toolContext) {
				return result;
			}

		};
		return new PseudonymizingToolCallback(tool, json);
	}

}
