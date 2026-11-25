# The chat is a non-modal panel docked in the app shell

The chat opens as a panel on the right of the signed-in shell (`AppShell`), from a button in the header, and it stays open while the user moves around the app. It is not a route and not a modal drawer. A ticket reference in an answer is a link that opens the ticket in the main area, next to the conversation, and the conversation stays where it was. On a narrow screen the panel takes the full width and closes to show the page behind it. The panel's state lives in a component the shell keeps mounted, so it survives navigation without any store, and a page reload starts a new conversation (chat ADR 0005).

The reason is the demo and the way the chat is used: a support person asks "which Acme tickets wait for the customer?", opens one of the answers, and reads the ticket beside the list the chat gave them. The cost is one slot and one button in the shell, which replace the route and the navigation entry on the seam list of chat ADR 0001.

## Considered Options

- **A route of its own, `/chat`, with a navigation entry.** This was the original seam. It was rejected because following a ticket link leaves the chat, so the conversation has to survive unmounting, either in a store or by restoring it from the server.
- **A modal drawer** (Chakra's `Drawer`). This was rejected because its overlay and focus trap cover the ticket the user just opened from the answer.

## Consequences

- The shell's layout changes from one centred column to a column plus an optional side panel, which every page renders inside. Pages must stay usable at the narrower width the open panel leaves them.
- The panel is mounted for every signed-in page, so it must cost nothing until it is opened: no request before the first question.
