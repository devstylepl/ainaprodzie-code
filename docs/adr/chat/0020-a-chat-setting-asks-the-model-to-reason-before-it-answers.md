# A chat setting asks the model to reason before it answers

Neither committed alias returns the text of its reasoning unless the request asks for it. Whether a question asks is a chat setting, `reasoning`, stored with the others and chosen by support staff on the Settings page. The app sends it on every model call of the chat as the request's `reasoning_effort` (`ReasoningEffort`, through `OpenAiChatOptions.reasoningEffort`):

| `reasoning` | Request field |
|---|---|
| `OFF`, the default | none: the request is the one the chat sent before the setting existed |
| `MEDIUM` | `reasoning_effort: medium` |
| `HIGH` | `reasoning_effort: high` |

It is a setting because lesson 1.3 shows one model parameter by its effect on time, tokens and cost, on one question, without a restart. `OFF` is the default, so a fresh setup sends the request it sent before and the lesson turns the parameter on.

The setting offers only efforts every alias of `litellm/config.yaml` accepts. An alias that rejects an effort answers 400, and unless `fallback` is `OFF` its fallback model answers in its place with 200 (chat ADR 0019), so nobody sees that the effort never applied.

What the model reasons streams to the reader as an event of its own, `reasoning`, with one property, `text`, the next piece, shaped like `token` (chat ADR 0004). Consecutive `reasoning` events form one block, which any other event ends; a block has no id, and the event carries the text alone, with no summary beside it. A turn shows one block: reasoning that arrives after another event joins the folded text, and the step keeps the first block's seconds. It streams because a model that reasons before it answers otherwise leaves the reader with nothing on screen but the waiting state. The SPA follows the events, not the setting: a turn that brings none looks as it did before the event existed.

Reasoning never counts as the answer. The hold-back until the first letter or digit applies to the answer alone, and a turn that reasons and writes no usable answer ends as `failed`. The reasoning lives in the stream and in the reader's browser alone: the conversation's memory takes the question and the answer without it (chat ADR 0005), and the app writes it to no span and no log line.

## Seen in the live check

Through LiteLLM v1.108.8, streamed, one arithmetic question sent straight to the proxy, on 2026-11-20. Time is to the end of the answer.

| alias | no `reasoning_effort` | `medium` | `high` |
|---|---|---|---|
| `chat-gemini` | no reasoning, 0.47 s, 12 completion tokens | 1261 characters of reasoning, 3.2 s, 1127 tokens | 2063 characters, 5.2 s, 1938 tokens |
| `chat-anthropic` | no reasoning text, 274 reasoning tokens, 2.1 s | 422 characters, 2.4 s, 357 tokens | 471 characters, 2.7 s, 417 tokens |
| `chat-chaos` | 200 | 200 | 200 |

Through the app with Spring AI 2.1.0 on `chat-gemini`, one question per choice, on 2026-11-22:

| `reasoning` | Time | Completion tokens | Of them reasoning | Cost |
|---|---|---|---|---|
| `OFF` | 0.9 s | 93 | none | $0.0003 |
| `MEDIUM` | 3.5 s | 891 | 809 | $0.0023 |
| `HIGH` | 2.7 s | 898 | 818 | $0.0023 |

- In Phoenix the effect is on LiteLLM's `litellm_request` span: `reasoning_effort` among its invocation parameters, the reasoning tokens as `llm.token_count.completion_details.reasoning`, and the cost. The app's model span counts the reasoning tokens among its output tokens and names neither the effort nor their share.
- Neither span holds the reasoning's text: both hold the answer alone.
- The text arrives in `delta.reasoning_content`, always before the first answer token, and Spring AI hands each chunk's piece over as `ReasoningPart.text()` of `getOutput().getReasoning()`; a chunk that holds only reasoning has an empty answer text.
- `chat-gemini` writes sections under a bold Markdown title, `chat-anthropic` plain prose word by word.
- `chat-gemini` sent its reasoning in a burst after two seconds of silence: under `MEDIUM` the step read "Thought for 1 s" on a question that took 3 seconds, and under `HIGH` "Thought for 4 s".

## Considered Options

- **`xhigh`.** Rejected: `chat-gemini` rejects it, and its fallback model `chat-anthropic` answered the question with 200.
- **`low`.** Rejected: both aliases accept it, but `chat-gemini` reasons under it and `chat-anthropic` does not, so the choice would show its effect on one alias only.

## Consequences

- The choice applies from the next question: the settings are read once per question.
- `OFF` leaves the provider's own default in force. `chat-anthropic` reasons on every question without being asked, at about 300 tokens, and returns no text of it; #334 decides whether `OFF` should send `minimal`.
- The seconds on the folded step are the browser's, from the first `reasoning` event to the first other event, so they leave out the silence before the first piece.
- The reasoning is the provider's text: English also under a Polish question, and it may quote the system prompt, which is no secret.
- The chat with tools, on `lesson-1` and later, can reason between its tool calls. Until #333 gives each of those its own block and restores the names, a turn there folds them into one step and shows placeholders in the text; nothing leaks.
- A reasoning answer has the same time as any other: `spring.ai.openai.timeout` for one model call and `retrofit.chat.turn-timeout` for the turn (chat ADR 0004).
- Only `ChatService` reads the setting, so a model call made elsewhere carries no `reasoning_effort` from it.
- An alias added to `litellm/config.yaml` has to accept `medium` and `high`. No test holds it to that, since only its provider can say.
