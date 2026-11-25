package app.retrofit;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;

import app.retrofit.organization.CallerLookup;

/// One real server and one database for every server suite. Suites extend this class instead of declaring their own
/// annotations, so the Spring test context cache shares a single context across them.
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class RandomPortContext {

	@Autowired
	protected JdbcClient jdbc;

	/// Counts how often an operation looks the signed-in user up. The spies of every suite live here, so the suites
	/// still share one context; each is reset after every test.
	@MockitoSpyBean
	protected CallerLookup callers;

	@LocalServerPort
	protected int port;

	/// A client for the running server that returns every status as a response instead of throwing.
	protected RestClient http() {
		return RestClient.builder()
				.baseUrl("http://localhost:" + port)
				.defaultStatusHandler(status -> true, (request, response) -> {
				})
				.build();
	}

	/// Fetches a CSRF token the way the SPA does: any response, the anonymous session 401 included, sets it.
	protected CsrfPair primeCsrf() {
		var response = http().get().uri("/api/auth/session").retrieve().toBodilessEntity();
		var token = cookieValue(response, "XSRF-TOKEN");
		if (token == null) {
			throw new AssertionError("GET /api/auth/session set no XSRF-TOKEN cookie");
		}
		return new CsrfPair("XSRF-TOKEN=" + token, token);
	}

	/// Signs in through the real endpoint and returns the `SESSION` cookie value.
	protected String login(String email, String password) {
		var csrf = primeCsrf();
		var response = http().post().uri("/api/auth/login")
				.header("X-XSRF-TOKEN", csrf.header())
				.header(HttpHeaders.COOKIE, csrf.cookie())
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("email", email, "password", password))
				.retrieve()
				.toBodilessEntity();
		var session = cookieValue(response, "SESSION");
		if (!response.getStatusCode().is2xxSuccessful() || session == null) {
			throw new AssertionError("Login of " + email + " failed with " + response.getStatusCode());
		}
		return session;
	}

	/// The value of cookie `name` set by the response, or `null` when it sets none.
	protected static String cookieValue(ResponseEntity<?> response, String name) {
		List<String> cookies = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
		String prefix = name + "=";
		return cookies.stream()
				.filter(cookie -> cookie.startsWith(prefix))
				.map(cookie -> cookie.substring(prefix.length()).split(";", 2)[0])
				.findFirst()
				.orElse(null);
	}

	/// A CSRF token as the SPA sends it: `cookie` for the Cookie header, `header` for X-XSRF-TOKEN.
	protected record CsrfPair(String cookie, String header) {
	}

}
