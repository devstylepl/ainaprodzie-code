# Retrofit Support

Customer support for a fictional SaaS project management product: customers report problems and questions as tickets, and support staff handle them across all organizations.

## People and organizations

**Organization**:
A customer company of the SaaS product; the boundary of what its customers may see.
_Avoid_: Tenant, company, account

**Customer**:
A person who belongs to exactly one organization and raises tickets on its behalf.
_Avoid_: User, client, end user

**Support staff**:
A person on the support side who belongs to exactly one team and handles tickets of every organization; never also a customer.
_Avoid_: Agent, operator, admin

**Team**:
A named group of support staff responsible for one area of the product, such as Identity, Projects or Billing.
_Avoid_: Group, queue, department

## Tickets

**Ticket**:
One customer request — a question, a bug or a feature request — raised within an organization and tracked until it is closed.
_Avoid_: Issue, case, request

**Reference number**:
The ticket's human-facing identifier, such as RET-1042, unique across all organizations.
_Avoid_: Ticket ID, ticket number, key

**Assignee**:
The one member of the ticket's team currently responsible for it; a ticket may have none.
_Avoid_: Owner, handler

**Public comment**:
A message on a ticket that both the customer's organization and support staff can read.
_Avoid_: Reply, message

**Internal note**:
A message on a ticket that only support staff can read.
_Avoid_: Private comment, hidden comment

**Related ticket**:
A ticket linked to another because they concern the same problem; the link reads the same from both sides and may cross organizations.
_Avoid_: Linked issue, sibling

**Potential duplicate**:
A ticket flagged as possibly reporting the same thing as an earlier one; the flag points from the later ticket to the earlier.
_Avoid_: Duplicate, copy

**Audit history**:
The record of every change to a ticket's fields and links: who changed which field, when, and its previous and new value.
_Avoid_: Changelog, activity log, timeline

## Status

**Waiting for customer**:
The status of a ticket where support staff have asked the customer something and await the answer.
_Avoid_: On hold, pending

**Resolved**:
The status of a ticket for which support staff have given a resolution that the customer has not yet confirmed or closed.
_Avoid_: Done, fixed

**Closed**:
The final status of a ticket, set by its customer or by support staff after resolution.
_Avoid_: Archived, finished

## Service level

**SLA**:
The time an organization is allowed to wait for the first response on each of its tickets, measured continuously, 24/7.
_Avoid_: Response time, target

**First response**:
The first public comment by support staff on a ticket.
_Avoid_: First reply, first contact

**Waiting time**:
The time from a ticket's creation to its first response; for a ticket closed without one, to its closing; otherwise to now.
_Avoid_: Age, idle time, time in status

**Response deadline**:
The ticket's creation time plus its organization's SLA.
_Avoid_: Due date, SLA deadline

**SLA breach**:
A ticket whose waiting time ends, or so far runs, past its response deadline.
_Avoid_: Overdue, late ticket

**Unanswered customer comment**:
A customer's public comment that is the latest public comment on a ticket in progress after its first response: support staff owe the next public comment. Before the first response the waiting time measures that wait instead.
_Avoid_: Customer reply, follow-up, new message

## Chat

**Placeholder**:
The numbered stand-in, such as `<PERSON_1>`, that the chat's model reads instead of a person's name; the user reads the name. It is the form a pseudonym takes: one person has one placeholder throughout a conversation.
_Avoid_: Mask, tag, token

**Lookalike**:
A text with the form of a placeholder that the chat did not give out, such as `<PERSON_1>` in a pasted log; the model reads it under a placeholder of its own, and the user reads it back as written.
_Avoid_: Fake placeholder, escaped placeholder

**Reasoning**:
The provider's summary of what the model thought through before the answer, not the model's raw thoughts.
_Avoid_: Thoughts, chain of thought
