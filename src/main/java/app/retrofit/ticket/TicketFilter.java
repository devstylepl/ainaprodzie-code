package app.retrofit.ticket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import app.retrofit.platform.FieldError;
import app.retrofit.platform.Validations;

/// Narrows a ticket list; all criteria apply together. A criterion left `null` or empty matches every ticket, and a
/// set matches a ticket whose value is any of its members.
///
/// `text` is either a reference (`RET-1042`, matched exactly) or text that must appear literally, ignoring case, in
/// the title or the description, starting at the start of a word: "sap" finds "SAP" but not "asap". Surrounding
/// whitespace is dropped, and blank text matches every ticket.
///
/// `createdFrom` and `createdTo` bound the creation time as a half-open range, the start included and the end
/// excluded, so consecutive ranges never share a ticket. A range that starts after it ends, and an instant whose UTC
/// year lies outside 1 to 9999, are a validation problem (422) for every caller, naming the field.
///
/// `deadlineBefore` keeps the tickets still awaiting a first response, not closed, whose response deadline falls
/// before this instant: those that will be breached by then unless answered, the breached ones included.
///
/// `attention` keeps the tickets one of the desk's counts holds ([TicketCounts]), measured against now.
///
/// Public because the chat reads tickets through [TicketQueries] from outside this package.
public record TicketFilter(@Nullable String text, @Nullable UUID organizationId, Set<TicketStatus> statuses,
		Set<TicketCategory> categories, Set<TicketPriority> priorities, @Nullable UUID teamId,
		@Nullable UUID assigneeId, @Nullable Boolean slaBreached, @Nullable UUID authorId,
		@Nullable Instant createdFrom, @Nullable Instant createdTo, @Nullable Instant deadlineBefore,
		@Nullable Attention attention) {

	public TicketFilter {
		text = text == null || text.isBlank() ? null : text.strip();
		statuses = Set.copyOf(statuses);
		categories = Set.copyOf(categories);
		priorities = Set.copyOf(priorities);
		var errors = new ArrayList<FieldError>();
		validate(errors, createdFrom, createdTo, deadlineBefore);
		Validations.throwIfInvalid(errors);
	}

	/// The constructor's checks without throwing, so a caller can report them with the page's in one problem.
	static void validate(List<FieldError> errors, @Nullable Instant createdFrom, @Nullable Instant createdTo,
			@Nullable Instant deadlineBefore) {
		var from = Validations.requireStorableInstant(errors, "createdFrom", createdFrom);
		var to = Validations.requireStorableInstant(errors, "createdTo", createdTo);
		Validations.requireStorableInstant(errors, "deadlineBefore", deadlineBefore);
		if (from != null && to != null && to.isBefore(from)) {
			errors.add(new FieldError("createdTo", "createdTo.invalid", "Must not be before createdFrom."));
		}
	}

}
