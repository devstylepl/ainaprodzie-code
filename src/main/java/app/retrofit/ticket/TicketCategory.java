package app.retrofit.ticket;

/// Spelled exactly as the `ticket_category_check` constraint spells it.
///
/// Public because the chat reads tickets through [TicketQueries] from outside this package.
public enum TicketCategory {

	BUG, QUESTION, FEATURE_REQUEST

}
