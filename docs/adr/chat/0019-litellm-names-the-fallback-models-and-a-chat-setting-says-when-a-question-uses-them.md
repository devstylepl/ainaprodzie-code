# LiteLLM names the fallback models, and a chat setting says when a question uses them

A provider that fails, or that limits the key's rate, ended the answer with an error, and a retry does not help against a limit that lasts a minute or a day. `router_settings.fallbacks` in `litellm/config.yaml` therefore names, per alias, the alias a question goes to when the provider of its own alias fails with an error or a timeout, its fallback model: `chat-gemini` falls back to `chat-anthropic`, and `chat-chaos` to `chat-gemini`, so that a fallback can be shown on demand with the one key every participant has. Which alias stands in for which is the gateway's fact, like the provider model behind an alias (chat ADR 0002), and `FallbackModelsTests` holds every fallback to an alias of the file.

When a question falls back is a chat setting, `fallback`, stored with the others and chosen by support staff on the Settings page. The app sends it on every model call as request fields that LiteLLM reads (`Fallback`, through `OpenAiChatOptions.extraBody`):

| `fallback` | Request field | A question whose provider fails |
|---|---|---|
| `OFF` | `disable_fallbacks: true` | is retried as chat ADR 0002 says, then ends with the error |
| `AFTER_RETRY` | none | is retried once by LiteLLM, then goes to the fallback model |
| `AT_ONCE`, the default | `num_retries: 0` | goes to the fallback model without a retry |

It is a setting, not one rule in `config.yaml`, because lesson 1.3 shows the three behaviours on one question, without an edit of the file and a restart of LiteLLM, and because "retry a rate limit or fall back on it" has no answer that fits every caller: a person waiting for an answer is better off with another model, a job in the background can wait. `AT_ONCE` is the default because it is the only state that fits a slow provider into the client's timeout (below), and because a retry of a rate limit spends the quota and gains nothing.

## Seen in the live check

On LiteLLM v1.108.8 with Spring AI 2.0.2, through the app, on `chat-chaos` with the fallback `chat-gemini`. An attempt is a request that reached the chaos provider.

| `fallback` | `429` | `1x 500` | `slow 20` |
|---|---|---|---|
| `OFF` | 4 attempts, `rate-limited`, 5.5 s | | |
| `AFTER_RETRY` | 2 attempts, answer, 3.1 s | 2 attempts, the chaos provider answers, 0.7 s | 2 attempts, answer, 33.1 s |
| `AT_ONCE` | 1 attempt, answer, 1.1 s | 1 attempt, the fallback answers, 1.0 s | 1 attempt, answer, 15.8 s |

- `AFTER_RETRY` against a slow provider ended 2 seconds before the client's 35 (`CHAT_MODEL_TIMEOUT`): two 15-second attempts, then the fallback's answer.
- A fallback that fails too answers with the error of the alias that was asked. With the fallback `chat-anthropic` and no `ANTHROPIC_API_KEY`, `429` ended with status 429 and the chat's `rate-limited`, after 4 attempts under `AFTER_RETRY` and 2 under `AT_ONCE`. A participant without that key loses nothing.
- A request with neither field retries a rate limit before it falls back (2 attempts): only `num_retries: 0` or a retry policy skips the retry.
- The fallback receives what the guardrail masked: the chaos provider, as a fallback, logged `<EMAIL_ADDRESS>` on every attempt (chat ADR 0017).
- The answer's chunks name the fallback's alias (`chat-gemini`), not the provider model, so the app's model span gets no second priced name (chat ADR 0015).
- In Phoenix a fallback is two LiteLLM traces while the app sends no `traceparent`: one failed, with the model `chaos` and no cost, one answered, with `gemini-3.5-flash-lite` and its cost. The answered span does not name the alias that was asked.

## Considered Options

- **Retry a rate limit, fall back on other errors** (the first option of #215). Rejected: a provider's limit lasts longer than the 35 seconds one call has, and the four attempts of one question spend the quota meanwhile.
- **A retry policy per error in `config.yaml`** (`model_group_retry_policy`: a rate limit and a timeout fall back at once, a 500 after a retry). It works: with it `429` and `slow 20` reached the chaos provider once and `500` twice. Not built: one fixed rule shows one behaviour, and the setting covers the two errors that need it. The price is the last column of `1x 500` above.
- **The fallback alias named by the app** (`fallbacks` in the request, which LiteLLM accepts from the app's key). Rejected: which alias may answer for another belongs with the aliases, where the tests hold each to the access group and the guardrail, and where a later lesson limits fallbacks to models its evaluations passed.
- **A chaos alias of its own for showing fallbacks**, beside `chat-chaos` without one. Not needed: `OFF` gives `chat-chaos` the retries of lesson 1.3 back.
- **A fallback for `chat-anthropic`.** Not built: two aliases that fall back to each other were not measured.

## Consequences

- Under `AT_ONCE`, LiteLLM retries no error of any alias. A single 500 that a retry would have answered goes to the fallback model, and an alias without one, such as `chat-anthropic`, is left with the SDK's retry: its failing question reaches the provider twice, not four times.
- A caller of the proxy that sends neither field gets `AFTER_RETRY`. One that must be answered by the alias it names, as an evaluation of that model must, sends `disable_fallbacks: true`: the chat's evaluations store `OFF` before every case (`ChatEvalSupport`), and the judge's aliases name no fallback. Their report shows whether that held: a case answered by a model the file does not name for its alias fails (chat ADR 0003).
- Unless the setting is `OFF`, a question can reach a second provider. The Data-flow window says so on its hop to the model provider. The chat does not say which model answered: the trace does, on LiteLLM's span, by the provider model and its cost.
- The chat calls tools, so one question makes several model calls, and a fallback can change the provider between two of them: the second provider then reads tool calls the first one made. That was not measured.
