package app.retrofit.chat;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import app.retrofit.ticket.TicketCategory;
import app.retrofit.ticket.TicketFilter;
import app.retrofit.ticket.TicketPriority;
import app.retrofit.ticket.TicketSort;
import app.retrofit.ticket.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/// A search as the `GET /api/tickets` query that lists the same tickets, under that operation's parameter names, with
/// the ids the search resolved its names to (chat ADR 0010). The page is left out: the list pages on its own.
@Schema(name = "ChatTicketListQuery")
record TicketListQuery(@Schema(requiredMode = REQUIRED, nullable = true) @Nullable String text,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable UUID organizationId,
		@Schema(requiredMode = REQUIRED) List<TicketStatus> status,
		@Schema(requiredMode = REQUIRED) List<TicketCategory> category,
		@Schema(requiredMode = REQUIRED) List<TicketPriority> priority,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable UUID teamId,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable UUID assigneeId,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable Boolean slaBreached,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable UUID authorId,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable Instant createdFrom,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable Instant createdTo,
		@Schema(requiredMode = REQUIRED, nullable = true) @Nullable Instant deadlineBefore,
		@Schema(requiredMode = REQUIRED) TicketSort sort) {

	static TicketListQuery of(TicketFilter filter, TicketSort sort) {
		return new TicketListQuery(filter.text(), filter.organizationId(), sorted(filter.statuses()),
				sorted(filter.categories()), sorted(filter.priorities()), filter.teamId(), filter.assigneeId(),
				filter.slaBreached(), filter.authorId(), filter.createdFrom(), filter.createdTo(),
				filter.deadlineBefore(), sort);
	}

	/// In declaration order, so one search always gives the same query string.
	private static <T extends Enum<T>> List<T> sorted(Set<T> values) {
		return values.stream().sorted().toList();
	}

}
