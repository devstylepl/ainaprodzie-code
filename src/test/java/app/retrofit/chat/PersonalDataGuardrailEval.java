package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.yaml.snakeyaml.Yaml;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// LiteLLM's guardrail, the only layer for pattern data (chat ADR 0017), as the provider receives what it
/// lets through: LiteLLM runs with the guardrail of `config.yaml` on an alias that has the guardrails of the chat's
/// alias, in front of a provider that records every request. It runs with the evaluations (chat ADR 0003), though no
/// model takes part.
@Tag("eval")
@org.testcontainers.junit.jupiter.Testcontainers
class PersonalDataGuardrailEval {

	private static final String ALIAS = "guarded";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final Network NETWORK = Network.newNetwork();

	private static final RecordingProvider PROVIDER = RecordingProvider.start();

	@Container
	static final GenericContainer<?> ANALYZER = ChatEvalConfiguration.presidioAnalyzer(NETWORK);

	@Container
	static final GenericContainer<?> ANONYMIZER = ChatEvalConfiguration.presidioAnonymizer(NETWORK);

	@Container
	static final GenericContainer<?> LITELLM = ChatEvalConfiguration
		.litellm(Transferable.of(config(PROVIDER.port())), NETWORK)
		.dependsOn(ANALYZER, ANONYMIZER);

	private final HttpClient http = HttpClient.newHttpClient();

	@BeforeEach
	void forgetRequests() {
		PROVIDER.requests.clear();
	}

	@AfterAll
	static void stopProvider() {
		PROVIDER.server.stop(0);
	}

	@Test
	void patternDataTheAppMissedReachesTheProviderMasked() {
		complete(false, List.of(
				message("user", "Write to jan.kowalski@acme.example or call +48 512 345 678 about RET-1027"),
				Map.of("role", "assistant", "content", "", "tool_calls", List.of(Map.of("id", "call-1", "type",
						"function", "function", Map.of("name", "findTicket", "arguments", "{\"reference\":\"RET-1045\"}")))),
				Map.of("role", "tool", "tool_call_id", "call-1", "content",
						"Pay from PL61 1090 1014 0000 0712 1981 2874, signatory PESEL 85071801246")));

		assertThat(contents()).anySatisfy(text -> assertThat(text).contains("<EMAIL_ADDRESS>", "<PHONE_NUMBER>"))
			.anySatisfy(text -> assertThat(text).contains("<IBAN_CODE>", "<PL_PESEL>"))
			.allSatisfy(text -> assertThat(text).doesNotContain("jan.kowalski@acme.example", "512 345 678",
					"PL61 1090", "85071801246"));
	}

	/// The app replaced what it found with its placeholders already, so the guardrail has nothing left to mask: the
	/// system prompt, a question and a tool result as the app sends them reach the provider as they are.
	@Test
	void aRequestTheAppMaskedReachesTheProviderUnchanged() throws IOException {
		var system = new ClassPathResource("prompts/chat-system.st").getContentAsString(StandardCharsets.UTF_8);
		var question = "Who did I ask you to remove in RET-1027, and is <PERSON_1> still at <EMAIL_ADDRESS_1>?";
		var result = """
				{"reference":"RET-1027","title":"Remove a user from our Taskline workspace","status":"RESOLVED",\
				"priority":"LOW","category":"ACCOUNT","createdAt":"2026-10-04T00:55:45Z","author":"<PERSON_1>",\
				"description":"Please remove <PERSON_2> (<EMAIL_ADDRESS_2>, <PHONE_NUMBER_1>) from Acme's workspace.",\
				"comments":[{"author":"<PERSON_3>","createdAt":"2026-10-05T09:12:00Z",\
				"text":"Done within 2 h 15 min, see RET-1028."}]}""";

		complete(false, List.of(message("system", system), message("user", question),
				Map.of("role", "assistant", "content", "", "tool_calls", List.of(Map.of("id", "call-1", "type",
						"function", "function", Map.of("name", "findTicket", "arguments", "{\"reference\":\"RET-1027\"}")))),
				Map.of("role", "tool", "tool_call_id", "call-1", "content", result)));

		assertThat(contents()).containsExactly(system, question, "", result);
	}

	/// The guardrail runs before the call and leaves the answer alone, so the answer streams as the provider sends it.
	@Test
	void theAnswerStillStreams() {
		var texts = complete(true, List.of(message("user", "Write to jan.kowalski@acme.example"))).lines()
			.filter(line -> line.startsWith("data: {"))
			.map(line -> JSON.readTree(line.substring("data: ".length())).at("/choices/0/delta/content").asString(""))
			.filter(text -> !text.isEmpty())
			.toList();

		assertThat(texts).isEqualTo(RecordingProvider.CHUNKS);
	}

	private String complete(boolean stream, List<Map<String, Object>> messages) {
		var body = JSON.writeValueAsString(Map.of("model", ALIAS, "stream", stream, "messages", messages));
		var request = HttpRequest.newBuilder(URI.create(ChatEvalConfiguration.litellmUrl(LITELLM) + "/chat/completions"))
			.header("Authorization", "Bearer " + ChatEvalConfiguration.MASTER_KEY)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
		try {
			var response = http.send(request, HttpResponse.BodyHandlers.ofString());
			assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
			return response.body();
		}
		catch (IOException ex) {
			throw new IllegalStateException("LiteLLM could not be called", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while calling LiteLLM", ex);
		}
	}

	private static Map<String, Object> message(String role, String content) {
		return Map.of("role", role, "content", content);
	}

	/// The text of every message of the one request the provider received.
	private static List<String> contents() {
		assertThat(PROVIDER.requests).hasSize(1);
		return PROVIDER.requests.getFirst()
			.get("messages")
			.valueStream()
			.map(message -> message.path("content").asString(""))
			.toList();
	}

	/// `config.yaml` with its guardrails and one alias, which has the guardrails of the chat's alias and calls the
	/// recording provider on the host.
	@SuppressWarnings("unchecked")
	private static String config(int providerPort) {
		try {
			Map<String, Object> config = new Yaml().load(Files.readString(ChatEvalConfiguration.LITELLM_CONFIG));
			var chat = ((List<Map<String, Object>>) config.get("model_list")).stream()
				.filter(model -> "chat-gemini".equals(model.get("model_name")))
				.findFirst()
				.orElseThrow();
			var guardrails = ((Map<String, Object>) chat.get("litellm_params")).getOrDefault("guardrails", List.of());
			Testcontainers.exposeHostPorts(providerPort);
			config.put("model_list", List.of(Map.of("model_name", ALIAS, "litellm_params",
					Map.of("model", "openai/recorded", "api_key", "sk-recorded", "api_base",
							"http://host.testcontainers.internal:" + providerPort + "/v1", "guardrails", guardrails))));
			config.remove("router_settings");
			return new Yaml().dump(config);
		}
		catch (IOException ex) {
			throw new IllegalStateException("litellm/config.yaml cannot be read", ex);
		}
	}

	/// An OpenAI-compatible provider that records each request's body and answers it with [#CHUNKS], streamed or in
	/// one message.
	private record RecordingProvider(HttpServer server, List<JsonNode> requests) {

		static final List<String> CHUNKS = List.of("The ", "ticket ", "is resolved.");

		static RecordingProvider start() {
			try {
				var provider = new RecordingProvider(HttpServer.create(new InetSocketAddress(0), 0),
						new CopyOnWriteArrayList<>());
				provider.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
				provider.server.createContext("/", exchange -> {
					var request = JSON.readTree(exchange.getRequestBody().readAllBytes());
					provider.requests.add(request);
					var stream = request.path("stream").asBoolean(false);
					var body = (stream ? streamed() : whole()).getBytes(StandardCharsets.UTF_8);
					exchange.getResponseHeaders()
						.add("Content-Type", stream ? "text/event-stream" : "application/json");
					exchange.sendResponseHeaders(200, body.length);
					try (var out = exchange.getResponseBody()) {
						out.write(body);
					}
				});
				provider.server.start();
				return provider;
			}
			catch (IOException ex) {
				throw new IllegalStateException("The recording provider cannot start", ex);
			}
		}

		int port() {
			return server.getAddress().getPort();
		}

		private static String whole() {
			return JSON.writeValueAsString(Map.of("id", "recorded-1", "object", "chat.completion", "created", 1,
					"model", "recorded", "choices", List.of(Map.of("index", 0, "finish_reason", "stop", "message",
							Map.of("role", "assistant", "content", String.join("", CHUNKS))))));
		}

		private static String streamed() {
			var events = new StringBuilder();
			for (var chunk : CHUNKS) {
				events.append("data: ")
					.append(JSON.writeValueAsString(Map.of("id", "recorded-1", "object", "chat.completion.chunk",
							"created", 1, "model", "recorded", "choices",
							List.of(Map.of("index", 0, "delta", Map.of("content", chunk))))))
					.append("\n\n");
			}
			return events.append("data: [DONE]\n\n").toString();
		}

	}

}
