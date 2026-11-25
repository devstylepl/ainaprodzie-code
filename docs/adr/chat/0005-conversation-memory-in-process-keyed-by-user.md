# Conversation memory lives in the process, keyed by the signed-in user

The chat remembers a conversation, so a follow-up such as "and which of them waits the longest?" works. The memory is Spring AI's `MessageWindowChatMemory` with a window of 20 messages, over the in-memory repository. The SPA names a conversation with a UUID from `crypto.randomUUID()`, but the server never uses that id alone: the memory key is `<userId>:<conversationId>`, built from the session's user id. Another user who sends the same conversation id reaches a different, empty conversation. `DELETE /api/chat/conversations/{conversationId}` forgets one conversation, and the SPA calls it for "New conversation". A page reload starts a new conversation too.

Only the user's questions and the final answers are kept, not the tool calls and their results. A follow-up therefore works from the previous answer's text, which names the tickets by reference, and the model calls a tool again when it needs the data. A turn that fails leaves nothing in memory, so a retry does not see half of the turn it repeats.

The reason is that memory is one of the concepts lesson 1 teaches, and the in-process window shows it with the least machinery: an advisor, a window and a key. The key is the second place, after the tool context (chat ADR 0002), where the server, not the request, decides whose data a call touches.

## Considered Options

- **No memory.** This was rejected because every question would stand alone, and follow-ups are the clearest difference between a chat and a search box.
- **Memory persisted with Spring AI's JDBC repository.** This was rejected for lesson 1 because it adds changelog files (a seam under chat ADR 0001) and a history endpoint for the SPA to restore from, and it teaches nothing the in-process window does not. Its `conversation_id` column is also `VARCHAR(36)`, too short for a key that includes the user.
- **The SPA sends the history with each question.** This was rejected because the client could then write the assistant's earlier turns itself, which is a prompt injection channel that bypasses the tools' visibility rules.
- **Conversations nobody deletes kept until the app restarts.** This was rejected because their placeholders hold, in plaintext, the personal data the lesson keeps from the model, for every conversation since startup.
- **The SPA deletes its conversation when the page goes away** (`pagehide` with a `keepalive` request). This was rejected as the limit because a crash, a killed browser or a lost network still leaves the conversation behind; only the server can bound how long it stays.
- **Forgetting a user's conversations at logout.** This was rejected because it would end the live conversation of the same user in another browser, the chat would have to learn of a logout from `auth` (chat ADR 0001), and an expired session raises no event to learn from.
- **A cache library with expiry** (Caffeine). This was rejected because it adds a dependency, a scheduler and a clock adapter for what a short sweep over the conversations does.

## Consequences

- A restart of the app forgets every conversation. The SPA shows this as an answer that no longer knows the earlier turns, never as an error.
- A conversation nobody asks in for 30 minutes (`retrofit.chat.conversation-idle-timeout`) is forgotten, its memory and its placeholders together (chat ADR 0009), by a sweep that runs every minute. The SPA never asks under an id again after a reload or a closed tab, so without it the personal data behind the placeholders would stay in the process until a restart. A user who comes back to a tab after that reads an answer that no longer knows the earlier turns, as after a restart.
- The memory belongs to one app instance, so a second instance behind a load balancer would need sticky sessions or a shared repository.
