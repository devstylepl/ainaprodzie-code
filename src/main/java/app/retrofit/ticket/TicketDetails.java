package app.retrofit.ticket;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;

/// `comments` and `history` are in chronological order.
///
/// Public because the chat reads tickets through [TicketQueries] from outside this package.
public record TicketDetails(@Schema(requiredMode = REQUIRED) TicketSummary summary,
		@Schema(requiredMode = REQUIRED, description = "Rises with every change; sent as the `ETag`") long version,
		@Schema(requiredMode = REQUIRED) String description,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable String resolution,
		@Schema(requiredMode = REQUIRED) List<TicketComment> comments,
		@Schema(requiredMode = REQUIRED) List<TicketAuditEntry> history,
		@Schema(requiredMode = REQUIRED) List<TicketLink> links) {
}
