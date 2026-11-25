package app.retrofit.ticket;

/// The order of a ticket list; ties fall back to the newest reference first.
///
/// Public because the chat reads tickets through [TicketQueries] from outside this package.
public enum TicketSort {

	RECENTLY_UPDATED,

	LONGEST_WAITING,

	/// The support queue: the open tickets still awaiting a first response come first, the earliest response deadline
	/// first (so the breached ones lead), then the other open tickets and last the closed ones, each of those two
	/// most recently updated first.
	RESPONSE_DEADLINE

}
