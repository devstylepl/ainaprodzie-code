# The gateway traces its model calls in a Phoenix project of its own

Superseded in part by chat ADR 0015: LiteLLM now traces into the app's project `retrofit`, since the app's model span is no longer priced and a question's trace needs both sides in one project. The callback and its single price stand.

LiteLLM sends its own trace of every model call to the Phoenix of `compose.yaml`, in the project `litellm`, while the app's spans go to the project `retrofit`. The gateway's span names the provider model it called, such as `gemini-3.5-flash-lite`, so Phoenix prices it without the app's `retrofit.chat.tracing.model-names`, and it covers every caller behind the gateway, not only the chat. The app's traces stay the place to read a question as a whole, with the tools it ran (chat ADR 0007). Each has a project of its own, so a model call traced by both is not priced twice in one project.

The gateway's span holds the prompt and the answer as the provider received them: the chat's placeholders (chat ADR 0009) and what the guardrail `personal-data` masked by its type, because LiteLLM logs the messages after its `pre_call` hooks. The gateway masks nothing more for its traces. The model reads a placeholder wherever the app or the guardrail put one, so its streamed answer holds the same placeholders.

LiteLLM 1.108.8 wraps each model call in a parent span, `litellm_proxy_request`, and gives it every attribute of the call's own span, the model and the tokens included, so Phoenix would price every call twice. `litellm/phoenix_callback.py` is LiteLLM's `arize_phoenix` callback with the parent span reduced to its kind, a chain, so the cost stays on the call's span alone. It overrides a method of LiteLLM's own, so an upgrade of the image checks it, and once LiteLLM stops copying the attributes ([BerriAI/litellm#50649](https://github.com/BerriAI/litellm/issues/50649)), `config.yaml` goes back to `arize_phoenix` and the file goes away.

The reason is the lesson: the gateway is where the provider model and its price are known, and showing what the provider received is how a participant sees that the masking held.

## Considered Options

- **LiteLLM's OpenTelemetry v2 (`LITELLM_OTEL_V2`).** This was rejected after a run of LiteLLM 1.108.3 against a collector. Its span names the alias in `llm.model_name`, the only attribute Phoenix prices by, and the provider model in `litellm.provider.model`, which Phoenix does not read. It also adds a console exporter that prints every span, prompts and answers included, to the container's log; naming an OTLP exporter silences it but exports every span twice.
- **The Presidio guardrail in `logging_only` mode, masking the traces.** This was rejected because under v2 the callback receives a copy of the messages and the response taken before the guardrail's logging hook runs, so the span keeps the text unmasked, streamed or not. A `logging_only` guardrail also runs on every request, whatever the alias lists, the judges included. Masking people there would take ticket references and product names for people, as it cannot be given the chat's allow-list.
- **One project for the app and the gateway.** This was rejected because a model call traced by both would be priced twice in it.
- **Tracing only in LiteLLM.** This was rejected in chat ADR 0007: the gateway does not see the tools the app runs between the model calls.
- **The doubled totals, accepted.** Each span would show the right cost, but a trace and the project would show twice the real one, and those are the numbers a lesson shows.

## Consequences

- A name the chat misses reaches the gateway's span as it reaches the provider (chat ADR 0009). The app's own spans still mask it at export (chat ADR 0007).
- The spans carry the metadata of the key that called: the app key's hash, alias, spend and budget, the caller's IP address and user agent, and the request's headers with the credential ones redacted. The callback has no switch for it. It describes the app's key, not a customer.
- The gateway's span starts a trace of its own: Spring AI's client calls LiteLLM on OkHttp's threads, without the question's `traceparent`. A question's model calls are found in the gateway's project by their time.
- Every model call also leaves spans of the guardrail's runs in the gateway's trace.
- When Phoenix does not answer, the gateway drops its spans; a model call neither waits nor fails.
- The evaluations' LiteLLM (chat ADR 0003) loads the callback too, with no Phoenix to send to, and drops its spans.
- `retrofit.chat.tracing.model-names` stays: the app's spans still name only the alias.
