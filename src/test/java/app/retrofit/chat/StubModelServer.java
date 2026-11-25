package app.retrofit.chat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistrar;

import com.sun.net.httpserver.HttpServer;

/// An OpenAI-compatible chat completions endpoint that streams scripted responses, as LiteLLM does with
/// `stream_options.include_usage`: the text or the tool call in chunks, then a chunk without choices that carries the
/// usage. It can refuse a request too, with the status and the JSON error LiteLLM would answer. It records every
/// request it receives. For the suites that run the real model client; the others script the model.
final class StubModelServer {

	/// One request as it arrived: its headers, under names of any case, and its JSON body.
	record Request(Map<String, List<String>> headers, String body) {

		@Nullable String header(String name) {
			var values = headers.get(name);
			return values == null ? null : values.getFirst();
		}

	}

	private static final String NO_USAGE = "{\"prompt_tokens\":10,\"completion_tokens\":2,\"total_tokens\":12}";

	private final HttpServer server;

	private final Queue<Response> responses = new ConcurrentLinkedQueue<>();

	private final List<Request> requests = new CopyOnWriteArrayList<>();

	private volatile Duration delay = Duration.ZERO;

	private volatile @Nullable String answeredBy;

	StubModelServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.createContext("/", exchange -> {
			var headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
			headers.putAll(exchange.getRequestHeaders());
			requests.add(new Request(headers,
					new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
			try {
				Thread.sleep(delay);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			var response = responses.size() > 1 ? responses.poll() : responses.peek();
			if (response == null) {
				response = new Response(200, MediaType.TEXT_EVENT_STREAM_VALUE, "", false);
			}
			var body = response.body().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, response.contentType());
			var providerModel = answeredBy;
			if (providerModel != null) {
				exchange.getResponseHeaders().add(AnsweringModelRecorder.PROVIDER_MODEL_HEADER, providerModel);
			}
			exchange.sendResponseHeaders(response.status(), response.cutOff() ? body.length + 1 : body.length);
			try (var out = exchange.getResponseBody()) {
				out.write(body);
				out.flush();
			}
		});
		server.start();
	}

	String url() {
		return "http://localhost:" + server.getAddress().getPort();
	}

	/// Every request from now on streams `text`, then `usage`, a JSON object as LiteLLM writes it.
	void answer(String text, String usage) {
		answer(text, usage, Duration.ZERO);
	}

	/// Every request from now on waits `delay` before it starts streaming `text` and `usage`.
	void answer(String text, String usage, Duration delay) {
		script(answerStream(text, usage));
		this.delay = delay;
	}

	/// The next requests stream these responses in order, the last one again for every later request, without delay;
	/// the requests recorded so far are forgotten.
	void script(String... streams) {
		respond(Stream.of(streams).map(stream -> new Response(200, MediaType.TEXT_EVENT_STREAM_VALUE, stream, false))
				.toList());
	}

	/// Every request from now on streams the start of a call of the tool `name`, its name and half of its arguments,
	/// and then loses the connection, as when a provider fails while the model writes the call.
	void cutOffWhileWriting(String name) {
		var frames = toolCallsWrittenInParts(name, "{\"reference\":\"RET-1\"}").split("(?<=\n\n)");
		respond(List.of(new Response(200, MediaType.TEXT_EVENT_STREAM_VALUE, frames[0] + frames[1], true)));
	}

	/// Every request from now on is refused with `status` and the JSON `error`, as LiteLLM refuses a request no
	/// provider gets to see; the requests recorded so far are forgotten.
	void refuse(int status, String error) {
		respond(List.of(new Response(status, MediaType.APPLICATION_JSON_VALUE, error, false)));
	}

	/// Every response of the script just set, a refusal too, names `providerModel` as the model that answered, in
	/// the `x-litellm-model-name` header LiteLLM sends; the next script names none again.
	void answeredBy(String providerModel) {
		answeredBy = providerModel;
	}

	private void respond(List<Response> scripted) {
		responses.clear();
		requests.clear();
		responses.addAll(scripted);
		delay = Duration.ZERO;
		answeredBy = null;
	}

	/// Every request since the script was set, oldest first.
	List<Request> requests() {
		return List.copyOf(requests);
	}

	/// A streamed answer `text`, then `usage`.
	static String answerStream(String text, String usage) {
		return stream(List.of(answerDelta(text)), "stop", usage);
	}

	/// A streamed answer that reasons first, as LiteLLM streams a model asked to: each piece of `reasoning` as the
	/// `reasoning_content` of a chunk without answer text, then `text` when it is not empty, then `usage`.
	static String reasoningStream(List<String> reasoning, String text, String usage) {
		var deltas = new ArrayList<String>();
		for (var piece : reasoning) {
			deltas.add("{\"role\":\"assistant\",\"content\":\"\",\"reasoning_content\":\"" + piece + "\"}");
		}
		if (!text.isEmpty()) {
			deltas.add(answerDelta(text));
		}
		return stream(deltas, "stop", usage);
	}

	private static String answerDelta(String text) {
		return "{\"role\":\"assistant\",\"content\":\"" + text + "\"}";
	}

	/// A streamed call of the tool `name` without arguments.
	static String toolCallStream(String name) {
		return stream("{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"index\":0,\"id\":\"call-"
				+ UUID.randomUUID() + "\",\"type\":\"function\",\"function\":{\"name\":\"" + name
				+ "\",\"arguments\":\"{}\"}}]}", "tool_calls", NO_USAGE);
	}

	/// Streamed calls of the tool `name` in one response, one per element of `arguments`, as a provider sends calls
	/// the model is still writing: a call's name comes first, without arguments, then its arguments in two halves.
	static String toolCallsWrittenInParts(String name, String... arguments) {
		var deltas = new ArrayList<String>();
		for (var index = 0; index < arguments.length; index++) {
			var written = arguments[index];
			var half = written.length() / 2;
			deltas.add("{\"tool_calls\":[{\"index\":" + index + ",\"id\":\"call-" + UUID.randomUUID()
					+ "\",\"type\":\"function\",\"function\":{\"name\":\"" + name + "\",\"arguments\":\"\"}}]}");
			for (var fragment : List.of(written.substring(0, half), written.substring(half))) {
				deltas.add("{\"tool_calls\":[{\"index\":" + index + ",\"function\":{\"arguments\":\""
						+ fragment.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}}]}");
			}
		}
		return stream(deltas, "tool_calls", NO_USAGE);
	}

	private static String stream(String delta, String finishReason, String usage) {
		return stream(List.of(delta), finishReason, usage);
	}

	private static String stream(List<String> deltas, String finishReason, String usage) {
		var chunk = "{\"id\":\"chatcmpl-" + UUID.randomUUID() + "\",\"object\":\"chat.completion.chunk\","
				+ "\"created\":1,\"model\":\"chat-gemini\",";
		var stream = new StringBuilder();
		for (var delta : deltas) {
			stream.append("data: ").append(chunk).append("\"choices\":[{\"index\":0,\"delta\":").append(delta)
					.append(",\"finish_reason\":null}]}\n\n");
		}
		return stream.append("data: ").append(chunk).append("\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"")
				.append(finishReason).append("\"}]}\n\n")
				.append("data: ").append(chunk).append("\"choices\":[],\"usage\":").append(usage).append("}\n\n")
				.append("data: [DONE]\n\n")
				.toString();
	}

	void stop() {
		server.stop(0);
	}

	/// `cutOff` announces a longer body than is sent, so the client loses the connection after `body`.
	private record Response(int status, String contentType, String body, boolean cutOff) {
	}

	/// The stub as the model client's endpoint.
	@TestConfiguration(proxyBeanMethods = false)
	static class Wiring {

		@Bean(destroyMethod = "stop")
		StubModelServer stubModelServer() throws IOException {
			return new StubModelServer();
		}

		@Bean
		DynamicPropertyRegistrar modelServerProperties(StubModelServer modelServer) {
			return registry -> {
				registry.add("spring.ai.openai.base-url", modelServer::url);
				registry.add("spring.ai.openai.api-key", () -> "sk-stub");
			};
		}

	}

}
