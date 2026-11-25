package app.retrofit.chat;

import static app.retrofit.organization.CallerLookup.callerId;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import app.retrofit.platform.FieldError;
import app.retrofit.platform.Validations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat/conversations")
@Tag(name = "chat", description = "Questions about the tickets the signed-in user may see, answered by a model that "
		+ "reads them as that user.")
class ChatController {

	static final int TEXT_MAX = 2000;

	static final String TICKET_REFERENCE = "^[Rr][Ee][Tt]-\\d{1,18}$";

	private static final Pattern TICKET_REFERENCE_FORMAT = Pattern.compile(TICKET_REFERENCE);

	record ChatMessageRequest(
			@Schema(requiredMode = REQUIRED, minLength = 1, maxLength = TEXT_MAX,
					description = "1 to 2,000 characters after trimming") @Nullable String text,
			@Schema(pattern = TICKET_REFERENCE, example = "RET-1042",
					description = "The ticket open on the user's page when they asked, which \"this ticket\" in the "
							+ "question means; absent on any other page") @Nullable String openTicket) {
	}

	private final ChatService chat;

	ChatController(ChatService chat) {
		this.chat = chat;
	}

	@Operation(summary = "Ask a question in a conversation and stream the answer",
			description = "The answer is a text/event-stream whose event names are the events' types: reasoning "
					+ "events with the model's reasoning when it returns any, tool events while the model calls "
					+ "tools, each after a toolStarted event when the provider streams the call while the model "
					+ "writes it, a tickets event for each tool call that returned tickets, token events with the "
					+ "answer text, then done, or error in its place. A failure before the stream starts is an "
					+ "ordinary problem response.")
	@ApiResponse(responseCode = "200", description = "The answer as server-sent events",
			content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
					schema = @Schema(implementation = ChatEvent.class)))
	@ApiResponse(responseCode = "400", description = "Problem type slug: malformed-request (a conversationId that is "
			+ "not a UUID, or a body that cannot be read)",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Problem type slug: auth/unauthenticated",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "Problem type slug: auth/forbidden (missing CSRF token)",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "Problem type slug: validation (errors[].code field.required, "
			+ "field.too-long or field.invalid)",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping(path = "/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	Flux<ServerSentEvent<ChatEvent>> ask(Authentication authentication, @PathVariable UUID conversationId,
			@RequestBody ChatMessageRequest body,
			@Parameter(description = "The interface language, en or pl; en when absent or neither")
			@RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) @Nullable String acceptLanguage) {
		var errors = new ArrayList<FieldError>();
		var text = validText(body.text(), errors);
		var openTicket = validOpenTicket(body.openTicket(), errors);
		Validations.throwIfInvalid(errors);
		var question = new ChatService.Question(callerId(authentication), conversationId,
				InterfaceLanguage.fromAcceptLanguage(acceptLanguage), text, openTicket);
		return chat.ask(question).map(event -> ServerSentEvent.builder(event).event(event.type()).build());
	}

	@Operation(summary = "Forget a conversation of the signed-in user")
	@ApiResponse(responseCode = "204", description = "Forgotten, or never started")
	@ApiResponse(responseCode = "400", description = "Problem type slug: malformed-request (a conversationId that is "
			+ "not a UUID)",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Problem type slug: auth/unauthenticated",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "Problem type slug: auth/forbidden (missing CSRF token)",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ProblemDetail.class)))
	@DeleteMapping("/{conversationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void forget(Authentication authentication, @PathVariable UUID conversationId) {
		chat.forget(callerId(authentication), conversationId);
	}

	/// The question trimmed, which is how the model reads it and memory keeps it, with the people it names
	/// as placeholders (chat ADR 0009).
	private static String validText(@Nullable String text, List<FieldError> errors) {
		var trimmed = text == null ? "" : text.strip();
		if (trimmed.isEmpty()) {
			errors.add(new FieldError("text", "field.required", "Must not be blank."));
		}
		if (trimmed.codePointCount(0, trimmed.length()) > TEXT_MAX) {
			errors.add(new FieldError("text", "field.too-long", "Must be at most " + TEXT_MAX + " characters."));
		}
		return trimmed;
	}

	/// The reference in upper case, as the tools and the answers write it; the model reads it inside a block the
	/// application writes, so nothing but a reference may reach it.
	private static @Nullable String validOpenTicket(@Nullable String openTicket, List<FieldError> errors) {
		if (openTicket == null) {
			return null;
		}
		if (!TICKET_REFERENCE_FORMAT.matcher(openTicket).matches()) {
			errors.add(new FieldError("openTicket", "field.invalid", "Must be a ticket reference such as RET-1042."));
			return null;
		}
		return openTicket.toUpperCase(Locale.ROOT);
	}

}
