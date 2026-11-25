package app.retrofit.chat;

import java.util.Arrays;
import java.util.List;

import io.opentelemetry.api.common.AttributeKey;

/// The span attributes that carry the chat's texts, under the GenAI semantic convention names (chat ADR 0007).
/// [ChatTraceContentFilter] writes a prompt, an answer, or a tool's arguments or result only under one of these, and
/// [MaskingSpanExporter] masks every one of them: a text attribute added here is masked with the others.
enum TraceText {

	INPUT_MESSAGES("gen_ai.input.messages"),

	OUTPUT_MESSAGES("gen_ai.output.messages"),

	TOOL_CALL_ARGUMENTS("gen_ai.tool.call.arguments"),

	TOOL_CALL_RESULT("gen_ai.tool.call.result");

	static final List<AttributeKey<String>> KEYS = Arrays.stream(values()).map(TraceText::key).toList();

	private final AttributeKey<String> key;

	TraceText(String name) {
		this.key = AttributeKey.stringKey(name);
	}

	AttributeKey<String> key() {
		return key;
	}

}
