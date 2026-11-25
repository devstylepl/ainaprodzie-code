package app.retrofit.ticket;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/// Public because the chat reads tickets through [TicketQueries] from outside this package.
public record TicketLink(@Schema(requiredMode = REQUIRED) LinkRelation relation,
		@Schema(requiredMode = REQUIRED) String reference,
		@Schema(requiredMode = REQUIRED) String title,
		@Schema(requiredMode = REQUIRED) TicketStatus status) {
}
