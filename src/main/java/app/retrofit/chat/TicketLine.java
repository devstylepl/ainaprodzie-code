package app.retrofit.chat;

import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

import app.retrofit.ticket.NamedRef;
import app.retrofit.ticket.TicketCategory;
import app.retrofit.ticket.TicketPriority;
import app.retrofit.ticket.TicketStatus;
import app.retrofit.ticket.TicketSummary;

/// One ticket as the model reads it: names instead of ids, so no UUID reaches the model, and the waiting time both as
/// text and in minutes.
record TicketLine(String reference, String title, String organization, String author,
		@Nullable TicketCategory category, TicketPriority priority, TicketStatus status, @Nullable String team,
		@Nullable String assignee, Instant createdAt, Instant updatedAt, @Nullable Instant firstResponseAt,
		@Nullable Instant closedAt, String waitingTime, long waitingMinutes, Instant responseDeadline,
		boolean slaBreached) {

	static TicketLine of(TicketSummary ticket) {
		var waiting = ticket.sla().waitingTime();
		return new TicketLine(ticket.reference(), ticket.title(), ticket.organization().name(), ticket.author().name(),
				ticket.category(), ticket.priority(), ticket.status(), name(ticket.team()), name(ticket.assignee()),
				ticket.createdAt(), ticket.updatedAt(), ticket.firstResponseAt(), ticket.closedAt(), text(waiting),
				waiting.toMinutes(), ticket.sla().responseDeadline(), ticket.sla().breached());
	}

	/// `3 d 4 h`, `2 h 5 min` or `45 min`: the two largest units are enough to compare and to say.
	static String text(Duration duration) {
		if (duration.toDays() > 0) {
			return duration.toDays() + " d " + duration.toHoursPart() + " h";
		}
		if (duration.toHours() > 0) {
			return duration.toHours() + " h " + duration.toMinutesPart() + " min";
		}
		return duration.toMinutes() + " min";
	}

	private static @Nullable String name(@Nullable NamedRef ref) {
		return ref == null ? null : ref.name();
	}

}
