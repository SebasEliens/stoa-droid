# 001 Ask the Stoics

Status: implemented

## Goal

Ask a question, pick one or more of Marcus Aurelius, Seneca and Epictetus, and get a short answer that quotes
their texts verbatim.

## Out of scope

Chat history across restarts, conversation memory in prompts, model download, voice.

## Acceptance examples

| ID | Given | When | Then |
|---|---|---|---|
| S001-1 | a question that matches a passage | the user asks | the answer shows that passage as a quote card with philosopher, ref and translator |
| S001-2 | a question no passage matches ("smarth phones") | the user asks | a plain reply, never an error |
| S001-3 | the model misquotes, or cites a passage it was not given | the answer is shown | that quote is dropped; every quote shown is a verbatim slice of a retrieved passage |
| S001-4 | any question and any model output (empty, garbage, only bad quotes) | the answer completes | there is always a non-empty reply and nothing crashes |
| S001-5 | only one philosopher is selected | the user taps it | it stays selected |

## Edge cases

- No matching passage: S001-2.
- Empty, very long or non-English input: S001-4 (the Ask button is also disabled for blank input).
- No model on the phone: S001-1 and S001-2 run in demo mode.
- Model fails on the GPU backend: falls back to CPU. Not tested: needs a real model.
- Model invents a quote or id, empty or malformed output: S001-3, S001-4.
- One philosopher vs all three: S001-4, S001-5.
- Rotation keeps the chat (ViewModel). Process death loses it: known gap, not specified yet.
- Offline: always; the app makes no network calls.
