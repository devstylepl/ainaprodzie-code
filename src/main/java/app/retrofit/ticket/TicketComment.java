package app.retrofit.ticket;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/// Public because the chat reads tickets through [TicketQueries] from outside this package.
public record TicketComment(@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) NamedRef author,
		@Schema(requiredMode = REQUIRED) boolean fromSupport,
		@Schema(requiredMode = REQUIRED) boolean internal,
		@Schema(requiredMode = REQUIRED) String body,
		@Schema(requiredMode = REQUIRED) Instant createdAt) {
}
