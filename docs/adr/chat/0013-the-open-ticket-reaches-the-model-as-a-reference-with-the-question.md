# The open ticket reaches the model as a reference with the question

A question asked while a ticket is open beside the chat carries that ticket's reference, and nothing else about it. The SPA sends it as `openTicket` with the question; the chat puts it in a `<page>` block, written by the application, between the context block and the question: `<page>ticket RET-1048</page>`. The system prompt says that "this ticket", "it" or "here" in such a question mean that ticket, that the model reads it with `getTicket` when the question needs it, and that a question naming other tickets or asking about many is about those. The panel names the ticket above the question box, the reader can remove it for the questions that follow, and every question shows the ticket it was asked on.

The reason is the support person's daily work: they open a ticket and ask about it, and retyping a reference the page already shows is the friction a docked chat exists to remove (chat ADR 0006). A reference keeps the model's facts where chat ADR 0004 puts them, in tool calls the user sees: the trace shows "Reading ticket RET-1048" as for any other ticket. It also keeps visibility where chat ADR 0002 puts it: the reference is only a hint, and `getTicket` answers a ticket the user may not see as not found.

## Considered Options

- **The ticket's facts in the prompt**, its title, status and clock read by the application and written into the block. This was rejected because those facts would reach the model with no tool call, so the trace would no longer show where an answer's facts came from, and the system prompt's rule that every fact comes from a tool result would have an exception.
- **The reference in the context block.** This was rejected because memory keeps the question without the context block (chat ADR 0008): after the user opens another ticket, an earlier "summarise this ticket" would lose the ticket it meant. The `<page>` block stays with the question in memory, so a follow-up reads each question with its own ticket. Earlier turns never change, so the prefix that providers cache stays the same.
- **No context: the user names the ticket.** This was rejected because it is the friction the decision removes, and the panel's suggested questions could not be about the open ticket.

## Consequences

- The reference comes from the request, so a user can send any reference, or type a `<page>` block of their own. That changes nothing they may read, as for the context block in chat ADR 0008. The server accepts only a reference (`RET-` and digits), so nothing but a reference reaches the block from `openTicket`.
- The panel reads the ticket from the ticket page's query and never fetches it: a ticket that is loading, missing or not visible is not named and not sent, and the open chat costs no request (chat ADR 0006).
- The evaluations ask on a ticket's page (chat ADR 0003): about the open ticket, about the whole desk from a ticket's page, and a follow-up after the user opens another ticket.
