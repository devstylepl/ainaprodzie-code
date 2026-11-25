package app.retrofit.chat;

import java.util.List;

/// What `searchTickets` returns to the model: the matching tickets, the first of them, or why it could not search.
sealed interface TicketSearchResult {

	/// Every matching ticket. `total` counts them, so the model answers "how many" from it rather than from the length
	/// of the list.
	record Tickets(long total, int shown, List<TicketLine> tickets) implements TicketSearchResult {
	}

	/// The first `shown` of `total` matching tickets. `rest` says so in a sentence the model reads with the tickets,
	/// and what to do about the others: search again with a limit that covers them, or, past the most a search
	/// returns, say that it names the first ones. Two numbers and a list alone do not tell the model that the list is
	/// incomplete: it would state the length of the page as the count or name tickets no tool returned.
	record FirstTickets(long total, int shown, List<TicketLine> tickets, String rest) implements TicketSearchResult {
	}

	/// A criterion the server could not resolve, such as a name no visible organization has, named in `message`
	/// together with what the user may see, so the model can correct it or tell the user.
	record Unresolved(String message) implements TicketSearchResult {
	}

}
