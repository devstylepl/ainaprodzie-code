package app.retrofit.ticket;

/// Spelled exactly as the `ticket_status_check` constraint spells it.
///
/// Public because the chat reads tickets through [TicketQueries] from outside this package.
public enum TicketStatus {

	NEW, IN_PROGRESS, WAITING_FOR_CUSTOMER, RESOLVED, CLOSED;

	/// Whether support staff may set `target` from this status (ADR 0004). Nothing returns to NEW, a resolved ticket
	/// only reopens, and CLOSED is reached only through close.
	boolean canMoveTo(TicketStatus target) {
		return switch (this) {
			case NEW, IN_PROGRESS, WAITING_FOR_CUSTOMER -> target != this && target != NEW && target != CLOSED;
			case RESOLVED -> target == IN_PROGRESS;
			case CLOSED -> false;
		};
	}

}
