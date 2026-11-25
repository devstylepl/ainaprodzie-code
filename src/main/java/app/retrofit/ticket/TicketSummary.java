package app.retrofit.ticket;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;

/// Public because the chat reads tickets through [TicketQueries] from outside this package.
public record TicketSummary(@Schema(requiredMode = REQUIRED, example = "RET-1042") String reference,
		@Schema(requiredMode = REQUIRED) String title,
		@Schema(requiredMode = REQUIRED) NamedRef organization,
		@Schema(requiredMode = REQUIRED) NamedRef author,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable TicketCategory category,
		@Schema(requiredMode = REQUIRED) TicketPriority priority,
		@Schema(requiredMode = REQUIRED) TicketStatus status,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable NamedRef team,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable NamedRef assignee,
		@Schema(requiredMode = REQUIRED) Instant createdAt,
		@Schema(requiredMode = REQUIRED) Instant updatedAt,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable Instant firstResponseAt,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable Instant closedAt,
		@Schema(requiredMode = REQUIRED) ResponseSla sla,
		@Schema(requiredMode = REQUIRED, nullable = true, description = "When the unanswered customer comment was "
				+ "written: the latest public comment, by a customer, on a ticket in progress after its first "
				+ "response, so support staff owe the next public comment; null when there is none")
		@Nullable Instant unansweredCustomerCommentAt) {
}
