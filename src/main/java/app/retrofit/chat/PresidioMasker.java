package app.retrofit.chat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// Presidio, for the traces. Its analyzer finds the personal data in a text and its anonymizer replaces each finding
/// with its type in angle brackets, such as `<EMAIL_ADDRESS>` (chat ADR 0007). What it asks for, the entities, the
/// recognizers and the operator, comes from [PresidioRequests]. Each text gets [#TIMEOUT] for its calls.
///
/// It calls Presidio with a plain `HttpClient`, not with a client Spring observes: masking runs while spans are
/// exported, and an observed call would make new spans to export.
final class PresidioMasker implements PersonalDataMasker {

	static final Duration TIMEOUT = Duration.ofSeconds(2);

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

	private final URI analyzer;

	private final URI anonymizer;

	private final PresidioRequests requests;

	private final JsonMapper json;

	PresidioMasker(URI analyzerUrl, URI anonymizerUrl, PresidioRequests requests, JsonMapper json) {
		this.analyzer = endpoint(analyzerUrl, "analyze");
		this.anonymizer = endpoint(anonymizerUrl, "anonymize");
		this.requests = requests;
		this.json = json;
	}

	/// The endpoint under the base URL, keeping any path the base has, such as a proxy's prefix.
	private static URI endpoint(URI base, String path) {
		var text = base.toString();
		return URI.create((text.endsWith("/") ? text : text + "/") + path);
	}

	@Override
	public String mask(String text) {
		var deadline = Instant.now().plus(TIMEOUT);
		var findings = analyze(text, deadline);
		if (findings.isEmpty()) {
			return text;
		}
		var body = withText(requests.anonymizer(), text);
		body.put("analyzer_results", findings);
		var masked = post(anonymizer, body, deadline);
		var maskedText = masked.get("text");
		if (maskedText == null || !maskedText.isString()) {
			throw new MaskingUnavailable("Presidio's anonymizer answered without a text", null);
		}
		return maskedText.asString();
	}

	private JsonNode analyze(String text, Instant deadline) {
		return post(analyzer, withText(requests.analyzer(), text), deadline);
	}

	private static Map<String, Object> withText(Map<String, Object> section, String text) {
		var body = new HashMap<>(section);
		body.put("text", text);
		return body;
	}

	private JsonNode post(URI uri, Map<String, Object> body, Instant deadline) {
		var remaining = Duration.between(Instant.now(), deadline);
		if (remaining.isNegative() || remaining.isZero()) {
			throw new MaskerNotAnswering("Presidio did not answer within " + TIMEOUT, null);
		}
		var request = HttpRequest.newBuilder(uri)
				.timeout(remaining)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
				.build();
		try {
			var response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				throw new MaskingUnavailable("Presidio answered " + response.statusCode() + " at " + uri, null);
			}
			return json.readTree(response.body());
		}
		catch (HttpTimeoutException ex) {
			throw new MaskerNotAnswering("Presidio did not answer within " + TIMEOUT + " at " + uri, ex);
		}
		catch (IOException | JacksonException ex) {
			throw new MaskingUnavailable("Presidio could not be read at " + uri, ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new MaskingUnavailable("Interrupted while waiting for Presidio", ex);
		}
	}

}
