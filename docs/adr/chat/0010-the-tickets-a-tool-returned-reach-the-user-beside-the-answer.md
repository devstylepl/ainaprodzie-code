# The tickets a tool returned reach the user beside the answer, never through the model

After a tool call that read tickets, the answer stream carries a `tickets` event, one more member of chat ADR 0004's union. It holds the tickets as the user reads them in the ticket list: the `TicketSummary` items that `TicketQueries` gave the tool, so they are filtered by visibility (ADR 0001) and taken before the result is pseudonymized for the model (chat ADR 0009), with every name in full. `searchTickets` sends the page it found, `getTicket` the ticket it found. A search that found nothing or had a criterion it could not resolve, and a ticket that was not found, send none. The SPA draws the tickets the answer names as the ticket list draws them, with the status, the waiting time and the SLA breach, in the order the answer first mentions them, once the answer is complete. The model summarises and names references; it no longer lists the facts per ticket.

The reason is the lesson and the answers it fixes. Asked which new tickets breached the SLA, the model repeated "New (Normal priority)" nine times without a breach, and a table answer copied raw ISO instants out of the tool result. A fact the model retypes is a fact it can misstate. Facts the app takes from the tool's own result cannot be misstated, and participants see the pattern: the model narrates, the app shows the data.

A `tickets` event carries `toolCall`, the position among the turn's `tool` events of the call it answers, because order alone cannot tell. Spring AI runs the tool calls of a model response only after the advisor has reported all of them, so two searches in one response stream as `tool, tool, tickets, tickets`. The tool wrapper counts every call it runs in a counter of the turn. Spring AI runs the calls in the order the advisor reports them, and a call past the tool-call limit ends the turn instead of being skipped (`on-limit-exceeded: throw`), so the position is exact, also after a call that failed.

A search's event also carries its `GET /api/tickets` query: the same criteria with names resolved to the ids the tool used, `assignedToMe` and `raisedByMe` as the caller's id, and the sort. Only the server can build it, because only the server resolved the names. The SPA offers "Show these in the list" only when its list can hold that query. Today the list takes one category and one priority, and has no author, date or deadline filter. The address always carries the search's sort, because support's list opens on the response deadline.

## Considered Options

- **The model writes the facts**, in a Markdown list or table, with a stricter prompt. This was rejected because no prompt makes a model unable to misstate a status or a time. It only makes that rarer.
- **The SPA reads the tickets the answer names** with `GET /api/tickets/{reference}`. This was rejected because it costs a request per ticket, it gives a row to a ticket no tool returned, and the rows would show a later state than the one the answer was written from.
- **The rows from the result the model read, with its placeholders restored.** This was rejected because the original result is at hand in the tool, and a restored copy would depend on every value making the round trip.
- **A new ticket schema for the event** with only the fields a row needs. This was rejected because `TicketSummary` is the list's own item: the rows cannot show anything the user's list would not, and the SPA reuses the list's types.
- **The tool call's id**, which Spring AI puts only on the tool-call observation. This was rejected because the stream's correctness would then depend on the tracing setup.

## Consequences

- The rows are a snapshot from when the tool ran; the reference links to the live ticket.
- The event goes to the user only. The model never reads it, and memory does not keep it, so a ticket named in a later turn that no tool of that turn returned stays a plain link.
- The system prompt tells the model that the reader sees each named ticket's status, waiting time and SLA beside the answer. The evaluations (chat ADR 0003) still check the references an answer names, and also that it writes no ISO instant.
- A search sends up to 20 ticket summaries on the stream, next to what the model reads.
