# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Support staff (primary).** Agents in a support team (Identity, Projects, Billing) who work tickets across every customer organization all day: they scan the list for what is new, breached or close to its response deadline, open a ticket, read the conversation, reply, add internal notes, classify it, and change status, priority and assignment. When design decisions conflict, this user's daily work wins.
- **Customers.** People in a customer organization of the fictional project-management SaaS. They raise tickets, follow their organization's tickets, add public comments and close their own tickets. They visit occasionally, often frustrated, and need to know where their problem stands.
- **Training participants (the real audience).** Developers on a course about adding AI features to an existing application. They run the app, read its code and extend it lesson by lesson. They judge it as a believable, well-built product, and it should impress them through craft, not showpieces.

## Product Purpose

Retrofit is a support-ticket application for a fictional SaaS project-management product. It exists as the base of a training: `master` is the ticket app without AI, and the lesson branches add a read-only chat, a chat that performs operations, a knowledge base, automated triage with human review, and production hardening.

Success means the app works as a credible support desk on its own (participants believe it is a real product someone shipped) and is a clear example of modern practice that participants can follow and extend.

## Positioning

A complete, deliberately realistic support desk built to have AI retrofitted into it. `master` plays the existing "modern legacy" app participants did not design; each lesson's diff is exactly what that lesson teaches. The seed data is messy on purpose (similar problems with different resolutions, vague descriptions, SLA breaches, fictional personal data, prompt-injection attempts) so the AI features meet realistic input.

## Operating Context

- Support staff: a queue and a ticket at a time, all day, on a desktop browser. SLA pressure is continuous (24/7 clock, no business calendar), so waiting time, response deadline and breaches must read at a glance.
- Customers: occasional visits to raise a ticket or check on one, from desktop or mobile.
- Training: run locally (`./mvnw spring-boot:run`, demo data seeded under the `dev` profile), shown on a projector and on participants' laptops, inspected next to its source code and branch diffs.
- AI surfaces added on lesson branches must slot into the existing app (chat, knowledge base, triage queue with proposed changes, sources, escalation reason, waiting time and reviewer), not replace it.

## Capabilities and Constraints

- **Roles:** `USER` (customer, scoped to their organization) and `SUPPORT` (all organizations, member of a team). The backend enforces permissions and organization isolation; the UI only reflects them.
- **Core views:** ticket list with search (reference number, title, description) and filters kept in the URL, ticket details with conversation and audit history, new-ticket form, login.
- **Ticket model:** reference number (`RET-1001`), title, description, organization, author, category (bug, question, feature request), priority (low, normal, high, critical), status (new, in progress, waiting for customer, resolved, closed), team, optional assignee, timestamps, first response, resolution, related tickets and potential duplicates. Public comments versus internal notes customers never see.
- **Rules that shape the UI:** ticket writes through one command service, with the support field edit as one save (ADR 0002), conflict detection by version with idempotent retries (ADR 0003), the status lifecycle (ADR 0004).
- **Vocabulary:** `GLOSSARY.md` holds the preferred term per concept; UI copy uses it.
- **Stack (binding):** React SPA (Vite, no SSR) on Chakra UI v3; design values live once as semantic tokens in `frontend/src/theme.ts`; shared primitives in `frontend/src/components/ui`. TanStack Query, react-router, i18next.
- **Languages (binding):** every surface ships in English (default) and Polish; Polish copy runs longer and uses plural forms.
- **Open:** light theme only today (`colorScheme: 'light'`), but dark mode is not ruled out.
- **Out of scope:** sprints, epics, email integration, payments, CRM and monitoring integrations, a configurable workflow engine.

### Read-only chat (lesson 1)

Decisions: `docs/adr/chat/`.

- **Who:** both roles. The chat reads tickets as the signed-in user, through the same visibility as the rest of the app, so a customer gets only their organization's tickets and never internal notes. When chat design decisions conflict, support staff's queue work wins: "which Acme tickets wait for the customer?", then reading an answer next to the ticket it opened.
- **What:** answers about the tickets the user may see: status, priority, assignment, public comments, waiting time, response deadline and SLA breaches. It only reads. Asked to change a ticket, it says so and points to the ticket's page; operations arrive in lesson 2.
- **Where:** a non-modal panel docked on the right of the signed-in shell, opened from the header, kept open across navigation. A ticket reference in an answer is a link that opens the ticket in the main area. On a narrow screen the panel takes the full width and closes when a link is followed. On a ticket's page the chat knows that ticket: "this ticket" means it, the suggested questions are about it, and the reader can remove it for questions about the whole desk.
- **Tool calls are visible (product commitment):** the answer streams in after live entries for each tool the model calls, with the arguments it chose, so the user sees where the answer's facts come from. Later lessons keep this transparency rather than hiding it.
- **Memory:** a conversation remembers its last 20 messages for follow-ups. "New conversation" and a page reload start fresh; there is no history to return to.
- **Language:** it answers in the language of the question, otherwise in the language of the interface.
- **Optional:** the app builds and runs without the chat; a model that does not answer must leave the ticket desk usable.
- **Privacy:** support staff decide on the Settings page how much the app protects. With "Mask personal data in the app" on, the model reads personal data as placeholders and the app's traces leave masked; off, as on a fresh database, only LiteLLM's guardrail masks pattern data, such as e-mail addresses, before the provider, never names. The app's traces leave only while support staff allow them, and LiteLLM's traces and logs carry the prompts and answers as the provider receives them.
- **Ticket text is data:** titles, descriptions and comments are written by customers, and the seed includes prompt-injection attempts the chat must report, not follow.

## Brand Commitments

- The product name in the UI is **Retrofit**. The fictional project-management SaaS that customers use stays unnamed or generic; do not invent a brand for it.
- The app is a teaching example: what it shows should be a pattern worth copying.

## Evidence on Hand

- Demo data: `src/main/resources/db/seed/demo-data.sql` (three organizations Acme Corp, Globex and Initech with first-response SLAs of 4, 8 and 24 hours; teams Identity, Projects and Billing; fifty tickets RET-1001 to RET-1050 with conversations, internal notes, links and audit history). Seed accounts are listed in `README.md`.
- Requirements: `requirements.md`; decisions: `docs/adr/`; terms: `GLOSSARY.md`.
- Assets: `frontend/public/favicon.svg` only. No logo, no wordmark, no product imagery.
- `frontend/src/theme.ts` cites mockups in `analysis/design-context/mockups`; that directory is not in the repository.
- No real customers, testimonials, metrics or press exist. Never fabricate them.

## Product Principles

1. **The support agent's queue comes first.** Status, priority, SLA state and ownership are readable at a glance; density serves people who work here all day.
2. **Believable before impressive.** The wow comes from a tool that feels shipped and cared for, in its details and states, not from decoration.
3. **Every surface teaches.** UI code follows the documented standards and reads as an example participants can copy.
4. **AI arrives as a guest.** Later AI features fit the established app and keep a human in control of what they change; the base must leave room for them.
5. **The rules are visible.** Permissions, internal versus public, conflicts and the status lifecycle show up in the UI honestly rather than failing silently.

## Accessibility & Inclusion

- Target **WCAG 2.2 AA**, on top of `.maister/docs/standards/frontend/accessibility.md`: semantic HTML, full keyboard use with visible focus, 4.5:1 text contrast, never colour alone for status, priority or SLA state, labelled inputs, proper heading order, focus management in dialogs and route changes.
- Both languages get the same accessibility: labels and live messages are translated, and layouts hold with longer Polish strings.
