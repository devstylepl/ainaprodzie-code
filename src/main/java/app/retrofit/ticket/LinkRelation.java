package app.retrofit.ticket;

/// How a linked ticket relates to the ticket being read. A potential duplicate link points from the later ticket to
/// the earlier one, so it reads differently from each end.
///
/// Public because the chat reads tickets through [TicketQueries] from outside this package.
public enum LinkRelation {

	RELATED,

	POTENTIAL_DUPLICATE_OF,

	HAS_POTENTIAL_DUPLICATE

}
