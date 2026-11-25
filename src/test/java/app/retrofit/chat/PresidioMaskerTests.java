package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/// [PresidioMasker] against a Presidio that records what it receives, and against one that accepts connections and
/// never answers, as a hung gunicorn worker does. A working Presidio's answers are checked by [PresidioMaskerEval].
class PresidioMaskerTests {

	private static final String REQUESTS = "presidio/requests.yaml";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

	@Test
	void theAnalyzerReceivesTheRequestsFileWithTheText() throws IOException {
		try (var recordingPresidio = new RecordingPresidio(Map.of("/analyze", "[]"))) {
			masker(recordingPresidio.url()).mask("Mail jan.kowalski@acme.example");

			var expected = new HashMap<>(fileSection("analyzer"));
			expected.put("text", "Mail jan.kowalski@acme.example");
			assertThat(JSON.readTree(recordingPresidio.received("/analyze"))).isEqualTo(JSON.valueToTree(expected));
		}
	}

	@Test
	void theAnonymizerReceivesTheRequestsFileWithTheTextAndTheFindings() throws IOException {
		var finding = """
				[{"entity_type": "EMAIL_ADDRESS", "start": 5, "end": 30, "score": 0.9}]""";
		try (var recordingPresidio = new RecordingPresidio(Map.of("/analyze", finding,
				"/anonymize", """
						{"text": "Mail <EMAIL_ADDRESS>"}"""))) {
			masker(recordingPresidio.url()).mask("Mail jan.kowalski@acme.example");

			var expected = new HashMap<>(fileSection("anonymizer"));
			expected.put("text", "Mail jan.kowalski@acme.example");
			expected.put("analyzer_results", JSON.readTree(finding));
			assertThat(JSON.readTree(recordingPresidio.received("/anonymize"))).isEqualTo(JSON.valueToTree(expected));
		}
	}

	@Test
	void aPresidioThatDoesNotAnswerIsToldApartFromOneThatRefuses() throws IOException {
		try (var silentPresidio = new ServerSocket(0, 50, LOOPBACK)) {
			var presidio = masker(URI.create("http://" + LOOPBACK.getHostAddress() + ":" + silentPresidio.getLocalPort()));

			assertThatThrownBy(() -> presidio.mask("Mail jan.kowalski@acme.example"))
					.isInstanceOf(PersonalDataMasker.MaskerNotAnswering.class);
		}
	}

	@Test
	void requestsWithoutAnAnalyzerSectionStopTheStartNamingTheFile() {
		var withoutAnalyzer = new ByteArrayResource("""
				anonymizer:
				  anonymizers:
				    DEFAULT:
				      type: replace
				""".getBytes(StandardCharsets.UTF_8), "presidio/requests.yaml without analyzer");

		assertThatThrownBy(() -> PresidioRequests.read(withoutAnalyzer))
				.hasMessageContaining("presidio/requests.yaml without analyzer");
	}

	private static PresidioMasker masker(URI presidio) {
		return new PresidioMasker(presidio, presidio, PresidioRequests.read(new ClassPathResource(REQUESTS)), JSON);
	}

	/// A section of the requests file as SnakeYAML reads it, independently of [PresidioRequests].
	@SuppressWarnings("unchecked")
	private static Map<String, Object> fileSection(String name) throws IOException {
		try (InputStream file = new ClassPathResource(REQUESTS).getInputStream()) {
			Map<String, Object> all = new Yaml().load(file);
			return (Map<String, Object>) all.get(name);
		}
	}

	/// A Presidio on the loopback that answers each path with a fixed body and records the body it received.
	private static final class RecordingPresidio implements AutoCloseable {

		private final HttpServer server;

		private final Map<String, String> received = new ConcurrentHashMap<>();

		RecordingPresidio(Map<String, String> answers) throws IOException {
			server = HttpServer.create(new InetSocketAddress(LOOPBACK, 0), 0);
			answers.forEach((path, answer) -> server.createContext(path, exchange -> {
				try (exchange; var body = exchange.getRequestBody()) {
					received.put(path, new String(body.readAllBytes(), StandardCharsets.UTF_8));
					var bytes = answer.getBytes(StandardCharsets.UTF_8);
					exchange.sendResponseHeaders(200, bytes.length);
					exchange.getResponseBody().write(bytes);
				}
			}));
			server.start();
		}

		URI url() {
			return URI.create("http://" + LOOPBACK.getHostAddress() + ":" + server.getAddress().getPort());
		}

		String received(String path) {
			return received.get(path);
		}

		@Override
		public void close() {
			server.stop(0);
		}

	}

}
