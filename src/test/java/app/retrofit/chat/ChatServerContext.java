package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import app.retrofit.RandomPortContext;
import app.retrofit.TestUsers;
import app.retrofit.TestUsers.TestUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// The chat's server suites: the real server with the [ScriptedChatModel] choosing what a model would, and helpers
/// that ask questions and read the streamed answer as the SPA does. It adds no annotation to [RandomPortContext], so
/// the suites share its application context.
abstract class ChatServerContext extends RandomPortContext {

	static final JsonMapper JSON = JsonMapper.builder().build();

	/// The display name of every user [app.retrofit.TestUsers] creates.
	static final String TEST_USER = "Test User";

	private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
	};

	@Autowired
	ScriptedChatModel model;

	@Autowired
	PasswordEncoder passwordEncoder;

	TestUsers users;

	@BeforeEach
	void setUpUsers() {
		users = new TestUsers(jdbc, passwordEncoder);
	}

	String login(TestUser user) {
		return login(user.email(), user.password());
	}

	/// Asks `text` in a new conversation.
	Reply ask(String session, String text) {
		return ask(session, UUID.randomUUID(), text);
	}

	Reply ask(String session, UUID conversationId, String text) {
		return ask(session, conversationId, Map.of("text", text));
	}

	/// Asks `text` on the page of the ticket `openTicket`, as the SPA does while that ticket is open.
	Reply askOnTicketPage(String session, UUID conversationId, String openTicket, String text) {
		return ask(session, conversationId, Map.of("text", text, "openTicket", openTicket));
	}

	private Reply ask(String session, UUID conversationId, Map<String, String> body) {
		var csrf = primeCsrf();
		var response = http().post().uri("/api/chat/conversations/{conversationId}/messages", conversationId)
				.header("X-XSRF-TOKEN", csrf.header())
				.header(HttpHeaders.COOKIE, csrf.cookie() + "; SESSION=" + session)
				.header(HttpHeaders.ACCEPT_LANGUAGE, "pl")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.toEntity(String.class);
		var contentType = response.getHeaders().getContentType();
		return new Reply(response.getStatusCode(), contentType, response.getBody() == null ? "" : response.getBody());
	}

	HttpStatusCode forget(String session, UUID conversationId) {
		var csrf = primeCsrf();
		return http().delete().uri("/api/chat/conversations/{conversationId}", conversationId)
				.header("X-XSRF-TOKEN", csrf.header())
				.header(HttpHeaders.COOKIE, csrf.cookie() + "; SESSION=" + session)
				.retrieve()
				.toBodilessEntity()
				.getStatusCode();
	}

	/// Raises a ticket as the customer signed in with `session` and returns its reference.
	String raiseTicket(String session, String title) {
		return raiseTicket(session, title, "");
	}

	/// The same, with a description.
	@SuppressWarnings("unchecked")
	String raiseTicket(String session, String title, String description) {
		var response = post(session, "/api/tickets", Map.of("title", title, "description", description));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return (String) ((Map<String, Object>) response.getBody().get("summary")).get("reference");
	}

	void comment(String session, String reference, String body, boolean internal) {
		var response = post(session, "/api/tickets/" + reference + "/comments",
				Map.of("body", body, "internal", internal));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	/// The ticket as the REST API answers it to the user signed in with `session`.
	JsonNode ticket(String session, String reference) {
		var response = http().get().uri("/api/tickets/{reference}", reference)
				.header(HttpHeaders.COOKIE, "SESSION=" + session)
				.retrieve()
				.toEntity(String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return JSON.readTree(response.getBody());
	}

	private ResponseEntity<Map<String, Object>> post(String session, String path,
			Map<String, Object> body) {
		var csrf = primeCsrf();
		return http().post().uri(path)
				.header("X-XSRF-TOKEN", csrf.header())
				.header(HttpHeaders.COOKIE, csrf.cookie() + "; SESSION=" + session)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.toEntity(JSON_OBJECT);
	}

	/// One streamed event as the SPA reads it: the SSE event name and its JSON data.
	record Event(String name, JsonNode data) {
	}

	/// A whole response to a question: its status and, for a stream, its body.
	record Reply(HttpStatusCode status, MediaType contentType, String body) {

		List<Event> events() {
			var events = new ArrayList<Event>();
			for (var frame : body.split("\n\n")) {
				String name = null;
				var data = new StringBuilder();
				for (var line : frame.split("\n")) {
					if (line.startsWith("event:")) {
						name = line.substring("event:".length());
					}
					else if (line.startsWith("data:")) {
						data.append(line.substring("data:".length()));
					}
				}
				if (name != null) {
					events.add(new Event(name, JSON.readTree(data.toString())));
				}
			}
			return events;
		}

		/// The events named `name`, in the order they streamed.
		List<Event> events(String name) {
			return events().stream().filter(event -> event.name().equals(name)).toList();
		}

		List<String> names() {
			return events().stream().map(Event::name).toList();
		}

		/// The answer text, as the SPA joins the `token` events.
		String text() {
			return String.join("", events().stream()
					.filter(event -> event.name().equals("token"))
					.map(event -> event.data().get("text").asString())
					.toList());
		}

		/// A response that is not a stream: its problem body.
		JsonNode problem() {
			return JSON.readTree(body);
		}

	}

}
