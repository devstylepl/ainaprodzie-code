package app.retrofit.chat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.retrofit.platform.FieldError;
import app.retrofit.platform.Validations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/// Gated to SUPPORT by the security chain, not here.
@RestController
@RequestMapping("/api/chat")
@Tag(name = "chat")
class ChatSettingsController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private static final List<String> FALLBACKS = Arrays.stream(Fallback.values()).map(Fallback::name).toList();

	private static final List<String> REASONINGS = Arrays.stream(ReasoningEffort.values())
			.map(ReasoningEffort::name)
			.toList();

	private final ChatSettingStore store;

	private final List<String> models;

	ChatSettingsController(ChatSettingStore store, @Value("${retrofit.chat.models}") List<String> models) {
		if (models.isEmpty()) {
			throw new IllegalArgumentException("retrofit.chat.models must name at least one LiteLLM alias");
		}
		this.store = store;
		this.models = List.copyOf(models);
	}

	@Operation(summary = "The chat settings; support only")
	@ApiResponse(responseCode = "200", description = "The stored settings")
	@ApiResponse(responseCode = "401", description = "Problem type slug: auth/unauthenticated",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "Problem type slug: auth/forbidden (not support staff)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/settings")
	ChatSettings get() {
		return store.settings();
	}

	@Operation(summary = "Change the chat settings the body names, leaving the others as stored; support only")
	@ApiResponse(responseCode = "200", description = "The stored settings")
	@ApiResponse(responseCode = "400", description = "Problem type slug: malformed-request (body is not JSON)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Problem type slug: auth/unauthenticated",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "Problem type slug: auth/forbidden "
			+ "(not support staff, or missing CSRF token)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "Problem type slug: validation (errors[].code field.required "
			+ "for a blank model, fallback or reasoning, model.unknown, fallback.unknown, reasoning.unknown)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@PatchMapping("/settings")
	ChatSettings change(@RequestBody ChatSettingsChange change) {
		var errors = new ArrayList<FieldError>();
		if (change.model() != null) {
			Validations.requireAllowed(errors, "model", change.model(), models);
		}
		if (change.fallback() != null) {
			Validations.requireAllowed(errors, "fallback", change.fallback(), FALLBACKS);
		}
		if (change.reasoning() != null) {
			Validations.requireAllowed(errors, "reasoning", change.reasoning(), REASONINGS);
		}
		Validations.throwIfInvalid(errors);
		return store.change(change);
	}

	@Operation(summary = "The models the chat can use; support only")
	@ApiResponse(responseCode = "200", description = "The LiteLLM aliases in their configured order")
	@ApiResponse(responseCode = "401", description = "Problem type slug: auth/unauthenticated",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "Problem type slug: auth/forbidden (not support staff)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/models")
	ChatModels models() {
		return new ChatModels(models);
	}

}
