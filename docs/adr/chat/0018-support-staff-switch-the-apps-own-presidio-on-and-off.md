# Support staff switch the app's own protection of personal data on and off

The app protects personal data itself in two places: it replaces the people it knows, the ones its tool results name in their fields and the people the asker may see, with numbered placeholders before the model reads them (chat ADR 0009), and it sends the texts of its own spans through Presidio's anonymizer before they leave (chat ADR 0007). LiteLLM's `pre_call` guardrail `personal-data` masks e-mail addresses, phone numbers, IBANs and PESELs, and blocks a card number, before a request reaches the provider (chat ADR 0017), in both states of the switch. Another switch on the Settings page, "Mask personal data in the app", turns the app's two uses on and off together, so the course can show what the gateway's guardrail does alone.

- **Stored and global**, as the other switches (chat ADRs 0015 and 0016): the column `presidio_in_app` of `chat_setting`, part of `GET` and `PATCH /api/chat/settings`, support staff only. Off in its changeset, so a fresh database sends names to the provider.
- **On means the people and the spans.** The people placeholders come from the app's own data, and Presidio is the app's only for the spans. The column and the API field are still named `presidioInApp` (see the rename under Considered Options).
- **Off means no placeholder for a person and no Presidio call from the app.** `ChatService` reads the settings once per question; with the switch off it reads no directory, and the question's `ChatTurn` gives no person a placeholder. The question and the tool results reach the model with their names, the ones a result carries in its own fields (author, assignee) included, which the app replaces when the switch is on: the point of off is to show the gateway alone.
- **What the switch leaves alone.** A text that only looks like a placeholder gets one of its own, and a placeholder the model writes is read back as its value, in both states (chat ADR 0009). A conversation begun with the switch on therefore goes on after it is turned off: memory still names its people by their placeholders, a tool the model passes one to gets the name, and the user reads the name.
- **What the app never does, in either state:** it calls no Presidio on the question's path, so a Presidio that is down ends a question at the gateway, where the guardrail fails closed, and not in the app. The user reads `unavailable` in both states.
- **The app's spans.** `SwitchedSpanExporter` reads the settings once per batch: off for app traces drops the batch; on sends it through `MaskingSpanExporter` while the app masks, and straight to the exporters otherwise.
- **What reaches the provider with it off:** the question and the tool results as written, names included; only a text that looks like a placeholder is replaced, by one of its own. LiteLLM's guardrail masks e-mail addresses, phone numbers, IBANs and PESELs and blocks a request holding a card number, never people, so names reach the provider, LiteLLM's spans and spend logs, and, with app traces on, the app's spans in Phoenix.
- **What reaches the provider with it on:** the people the app knows as placeholders, and everything else as with it off. A name of somebody with no account, a name in a form the directory misses and the other items of chat ADR 0009 reach the provider as written.

## Considered Options

- **Wrapping the Presidio bean.** Rejected: the regular tests replace that bean with a stub, so the switch would never be tested on the path the app takes. The switch is read where the masking is used instead.
- **Off replaces nothing and puts nothing back in a tool's arguments.** Rejected for the conversation in which the switch is turned off: memory names a person by `<PERSON_1>`, the model passes that to `searchTickets`, the tool searches for the text `<PERSON_1>` and finds nothing, while the `tool` event shows the user the name. A `<PERSON_1>` in a log pasted after that would be read back as the person the conversation gave that placeholder to. The placeholders of a conversation outlive the switch, so the two rules that keep them readable hold in both states.
- **One switch per use.** Rejected: nobody asked for placeholders without masked spans or the reverse, and two switches double what the page has to explain.
- **On by default.** Rejected at the course's request: a fresh setup starts with the gateway as the only layer, and the app's layer is switched on during the lesson.
- **Renaming the column and the field to what they cover now.** Rejected: a rename is a changeset and a contract change on six branches, and nobody asked for it.

## Consequences

- A fresh database sends personal data, names included, to the provider until support staff turn the switch on. The README says so.
- After the switch is turned off in a conversation, a person an earlier turn gave a placeholder to is a name in the question and in a tool result and still a placeholder in memory, and the model may not tell that they are one person.
- The suites that check placeholders or masked spans turn the switch on before each test, as the evaluations do.
