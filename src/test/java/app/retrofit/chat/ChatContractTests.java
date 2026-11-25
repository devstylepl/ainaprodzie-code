package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/// The chat in the exported contract, which the SPA's types are generated from: its paths, and the events as one
/// union the SPA can switch on by `type` (chat ADR 0004).
class ChatContractTests extends ChatServerContext {

	@Test
	void theChatPathsAreInTheContract() {
		var paths = apiDoc().get("paths");

		assertThat(paths.get("/api/chat/conversations/{conversationId}/messages").has("post")).isTrue();
		assertThat(paths.get("/api/chat/conversations/{conversationId}").has("delete")).isTrue();
		assertThat(paths.get("/api/chat/conversations/{conversationId}/messages").get("post").get("responses")
				.get("200").get("content").get("text/event-stream").get("schema").get("$ref").asString())
				.isEqualTo("#/components/schemas/ChatEvent");
	}

	@Test
	void chatEventIsAOneOfWithATypeDiscriminator() {
		var schemas = apiDoc().get("components").get("schemas");
		var event = schemas.get("ChatEvent");

		assertThat(event.get("oneOf").valueStream().map(ref -> ref.get("$ref").asString())).containsExactly(
				"#/components/schemas/ChatReasoningEvent", "#/components/schemas/ChatToolCallStartedEvent",
				"#/components/schemas/ChatToolCallEvent", "#/components/schemas/ChatTicketsEvent",
				"#/components/schemas/ChatTokenEvent", "#/components/schemas/ChatDoneEvent",
				"#/components/schemas/ChatErrorEvent");
		assertThat(event.get("discriminator").get("propertyName").asString()).isEqualTo("type");
		assertThat(event.get("discriminator").get("mapping").get("error").asString())
				.isEqualTo("#/components/schemas/ChatErrorEvent");
		var error = schemas.get("ChatErrorEvent");
		assertThat(error.get("properties").get("type").get("enum").valueStream().map(JsonNode::asString))
				.containsExactly("error");
		assertThat(error.get("properties").get("code").get("enum").valueStream().map(JsonNode::asString))
				.containsExactly("rate-limited", "unavailable", "timeout", "guardrails-blocked", "failed");
		assertThat(error.get("required").valueStream().map(JsonNode::asString)).contains("type", "code");
	}

	@Test
	void theReasoningEventIsInTheUnionWithItsText() {
		var schemas = apiDoc().get("components").get("schemas");

		assertThat(schemas.get("ChatEvent").get("discriminator").get("mapping").get("reasoning").asString())
				.isEqualTo("#/components/schemas/ChatReasoningEvent");
		var reasoning = schemas.get("ChatReasoningEvent");
		assertThat(reasoning.get("properties").get("type").get("enum").valueStream().map(JsonNode::asString))
				.containsExactly("reasoning");
		assertThat(reasoning.get("properties").get("text").get("type").asString()).isEqualTo("string");
		assertThat(reasoning.get("required").valueStream().map(JsonNode::asString))
				.containsExactlyInAnyOrder("type", "text");
	}

	/// A call the model is still writing has a name and the number of its `tool` event, and no arguments yet.
	@Test
	void theToolStartedEventCarriesTheToolAndTheNumberOfItsCall() {
		var schemas = apiDoc().get("components").get("schemas");
		var started = schemas.get("ChatToolCallStartedEvent");

		assertThat(schemas.get("ChatEvent").get("discriminator").get("mapping").get("toolStarted").asString())
				.isEqualTo("#/components/schemas/ChatToolCallStartedEvent");
		assertThat(started.get("properties").propertyNames()).containsExactlyInAnyOrder("type", "tool", "toolCall");
		assertThat(started.get("properties").get("type").get("enum").valueStream().map(JsonNode::asString))
				.containsExactly("toolStarted");
		assertThat(started.get("properties").get("toolCall").get("type").asString()).isEqualTo("integer");
		assertThat(started.get("required").valueStream().map(JsonNode::asString))
				.containsExactlyInAnyOrder("type", "tool", "toolCall");
	}

	/// The rows beside an answer are the ticket list's own items, and only a search has a query (chat ADR 0010).
	@Test
	void theTicketsEventCarriesTicketSummariesAndANullableQuery() {
		var schemas = apiDoc().get("components").get("schemas");
		var tickets = schemas.get("ChatTicketsEvent");

		assertThat(schemas.get("ChatEvent").get("discriminator").get("mapping").get("tickets").asString())
				.isEqualTo("#/components/schemas/ChatTicketsEvent");
		assertThat(tickets.get("properties").get("tickets").get("items").get("$ref").asString())
				.isEqualTo("#/components/schemas/TicketSummary");
		assertThat(tickets.get("properties").get("query").get("anyOf").valueStream().map(JsonNode::toString))
				.containsExactly("{\"$ref\":\"#/components/schemas/ChatTicketListQuery\"}", "{\"type\":\"null\"}");
		assertThat(tickets.get("required").valueStream().map(JsonNode::asString))
				.contains("type", "toolCall", "tickets", "query");
	}

	/// The composer limits the question to the length the contract gives, rather than to a copy of the server's.
	@Test
	void theQuestionsLengthIsInTheContract() {
		var text = apiDoc().get("components").get("schemas").get("ChatMessageRequest").get("properties").get("text");

		assertThat(text.get("minLength").asInt()).isEqualTo(1);
		assertThat(text.get("maxLength").asInt()).isEqualTo(ChatController.TEXT_MAX);
	}

	/// The SPA sends the open ticket only on a ticket page, so the contract must let it leave the field out.
	@Test
	void theOpenTicketIsAnOptionalReference() {
		var request = apiDoc().get("components").get("schemas").get("ChatMessageRequest");

		assertThat(request.get("required").valueStream().map(JsonNode::asString)).containsExactly("text");
		assertThat(request.get("properties").get("openTicket").get("pattern").asString())
				.isEqualTo(ChatController.TICKET_REFERENCE);
	}

	private JsonNode apiDoc() {
		return JSON.readTree(http().get().uri("/v3/api-docs").retrieve().body(String.class));
	}

}
