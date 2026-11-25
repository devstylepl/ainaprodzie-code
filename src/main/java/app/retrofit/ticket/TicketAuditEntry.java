package app.retrofit.ticket;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;

/// One change in a ticket's audit history. Values are text as shown at the time of the change: enum names, the team
/// name, the assignee's display name, or for a link its type and the other ticket's reference. Display names are not
/// unique, so an assignee change between namesakes shows one name twice.
///
/// Public because the chat reads tickets through [TicketQueries] from outside this package.
public record TicketAuditEntry(@Schema(requiredMode = REQUIRED) NamedRef actor,
		@Schema(requiredMode = REQUIRED) Instant changedAt,
		@Schema(requiredMode = REQUIRED) AuditedField field,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable String oldValue,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable String newValue) {
}
