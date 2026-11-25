package app.retrofit.chat;

import static app.retrofit.chat.LiteLlmConfig.alias;
import static app.retrofit.chat.LiteLlmConfig.models;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.Transferable;
import org.yaml.snakeyaml.Yaml;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/// The caller's id stays in the gateway (chat ADR 0015): the app sends it as the request's `user`, and no provider
/// receives it, as none needs it, while LiteLLM's own spans still name the caller. LiteLLM hands `user` on to Anthropic
/// as `metadata.user_id`, so the provider here is an Anthropic API on the host that records each request, behind the
/// `chat-anthropic` alias of `config.yaml`, and Phoenix is a recorder of the spans LiteLLM exports.
class CallerIdTests {

	private static final String ALIAS = "chat-anthropic";

	private static final String CALLER = "0f8fad5b-d9cb-469f-a165-70867728950e";

	private static final String HOST = "host.testcontainers.internal";

	private static final Pattern USER_ID = Pattern.compile("user\\.id[\\s\\S]{1,4}" + CALLER);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final byte[] MESSAGE = JSON
		.writeValueAsString(Map.of("id", "msg_recorded", "type", "message", "role", "assistant", "model", "recorded",
				"stop_reason", "end_turn", "content", List.of(Map.of("type", "text", "text", "Two tickets.")), "usage",
				Map.of("input_tokens", 5, "output_tokens", 3)))
		.getBytes(StandardCharsets.UTF_8);

	private static Recorder provider;

	private static Recorder spans;

	private static Network network;

	private static GenericContainer<?> litellm;

	@BeforeAll
	static void askTheQuestion() throws Exception {
		provider = Recorder.start(MESSAGE);
		spans = Recorder.start(new byte[0]);
		Testcontainers.exposeHostPorts(provider.port(), spans.port());
		network = Network.newNetwork();
		litellm = ChatEvalConfiguration.litellm(Transferable.of(configOfTheAliasUnderTest()), network)
			.withEnv("PHOENIX_COLLECTOR_HTTP_ENDPOINT", "http://" + HOST + ":" + spans.port() + "/v1/traces")
			.withEnv("OTEL_BSP_SCHEDULE_DELAY", "100");
		litellm.start();

		var body = JSON.writeValueAsString(Map.of("model", ALIAS, "user", CALLER, "messages",
				List.of(Map.of("role", "user", "content", "Which tickets are mine?"))));
		var request = HttpRequest.newBuilder(URI.create(ChatEvalConfiguration.litellmUrl(litellm) + "/chat/completions"))
			.header("Authorization", "Bearer " + ChatEvalConfiguration.MASTER_KEY)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
		var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
	}

	@AfterAll
	static void stop() {
		litellm.stop();
		network.close();
		provider.stop();
		spans.stop();
	}

	@Test
	void theProviderReceivesNoUser() {
		assertThat(provider.requests()).singleElement().satisfies(received -> {
			var request = JSON.readTree(received);
			assertThat(request.path("messages").toString()).contains("Which tickets are mine?");
			assertThat(new String(received, StandardCharsets.UTF_8)).doesNotContain(CALLER);
			assertThat(request.has("user")).isFalse();
			assertThat(request.path("metadata").has("user_id")).isFalse();
		});
	}

	/// The dropped `user` gives LiteLLM's span no `user.id`: `phoenix_callback.py` sets it from the end user. OTLP's
	/// protobuf holds an attribute as its key followed by a value whose string is the caller's id.
	@Test
	void aSpanOfTheQuestionNamesTheCallerAsItsUser() {
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> assertThat(spans.requests().stream()
				.anyMatch(export -> USER_ID.matcher(new String(export, StandardCharsets.ISO_8859_1)).find()))
					.as("an export of LiteLLM's spans with user.id " + CALLER)
					.isTrue());
	}

	/// `config.yaml` with the one alias under test, which calls the recording provider on the host, and without the
	/// guardrail, which needs Presidio: what the provider receives from the alias is what `litellm_params` makes of it.
	@SuppressWarnings("unchecked")
	private static String configOfTheAliasUnderTest() {
		var config = LiteLlmConfig.read();
		var model = models().stream()
			.filter(candidate -> ALIAS.equals(alias(candidate)))
			.findFirst()
			.orElseThrow(() -> new AssertionError(ChatEvalConfiguration.LITELLM_CONFIG + " has no alias " + ALIAS));
		var parameters = (Map<String, Object>) model.get("litellm_params");
		parameters.put("api_key", "sk-recorded");
		parameters.put("api_base", "http://" + HOST + ":" + provider.port());
		parameters.remove("guardrails");
		config.put("model_list", List.of(model));
		config.remove("guardrails");
		config.remove("router_settings");
		return new Yaml().dump(config);
	}

	/// An HTTP server on the host that records the body of each request and answers every one with the same bytes.
	private record Recorder(HttpServer server, List<byte[]> requests) {

		static Recorder start(byte[] answer) {
			try {
				var recorder = new Recorder(HttpServer.create(new InetSocketAddress(0), 0),
						new CopyOnWriteArrayList<>());
				recorder.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
				recorder.server.createContext("/", exchange -> {
					recorder.requests.add(exchange.getRequestBody().readAllBytes());
					exchange.getResponseHeaders().add("Content-Type", "application/json");
					exchange.sendResponseHeaders(200, answer.length == 0 ? -1 : answer.length);
					try (var out = exchange.getResponseBody()) {
						out.write(answer);
					}
				});
				recorder.server.start();
				return recorder;
			}
			catch (IOException ex) {
				throw new IllegalStateException("The recording server cannot start", ex);
			}
		}

		int port() {
			return server.getAddress().getPort();
		}

		void stop() {
			server.stop(0);
		}

	}

}
