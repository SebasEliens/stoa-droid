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

## Device questions

- Lifecycle: the chat lives in the ViewModel. It survives rotation and is lost on process death and force-stop (D001-5).
- Memory: the model stays loaded for the life of the process (D001-6), which makes Stoa the first background app
  the low-memory killer takes. File-mapped vs private model memory is not measured yet (`dumpsys meminfo`, bench).
- Threads: model load and generation run off the main thread (D001-7).
- Hardware: GPU first, CPU if the GPU backend fails (D001-4).
- Storage: nothing is written by the app. The corpus is an APK asset (D001-2); the model is copied in by hand (D001-3).
- Data: passages are read once into memory; no schema to migrate.
- Privacy: no network, no permissions of its own (S001-6 once merged); the model folder is private to the app (D001-3).

## Decisions

Recorded on 2026-11-27 as the behaviour already built. Later specs (chat persistence, model swap, semantic
retrieval, model download) revisit them.

| ID | Area | Question | Options and trade-offs | Choice and why | Verified by |
|---|---|---|---|---|---|
| D001-1 | data | How are passages found? | BM25 keyword search: no model, tiny, explainable. Embeddings: better on paraphrases, costs a model and RAM. | **BM25, best 6 into the prompt** (owner, 2026-11-27) as built; embeddings get their own spec | eval |
| D001-2 | storage | Where do the texts live? | APK asset: ships with the app, read-only. Downloaded file: smaller APK, needs network. | **One `passages.jsonl` asset, read into memory on the first question** (owner, 2026-11-27) as built | S001-1 |
| D001-3 | privacy | Where does the model file live? | App-specific external storage: no permission, private to the app, deleted on uninstall. Shared storage: needs a permission, visible to other apps. | **`Android/data/dev.stoa.droid/files/models/`, copied in over adb** (owner, 2026-11-27) as built; in-app download gets its own spec | device |
| D001-4 | hardware | Which backend runs the model? | GPU: faster where supported. CPU: works everywhere, slower. | **GPU first, CPU if it fails** (owner, 2026-11-27) as built | bench |
| D001-5 | lifecycle | What happens to the chat when Android kills the app? | In memory: simple, lost on process death. Saved state or a file: survives, needs a save policy. | **Lost; kept only through rotation** (owner, 2026-11-27) as built; a known gap for the chat-persistence spec | device |
| D001-6 | memory | When is the model loaded and released? | Keep loaded: fast answers, largest background app, first to be killed. Release in background: frees RAM, slow next answer. | **Loaded on the first question, never released** (owner, 2026-11-27) as built; revisit with model swap | bench |
| D001-7 | threads | Where do load and generation run? | Main thread: freezes the UI (ANR). Background dispatcher: UI stays responsive. | **Off the main thread** (owner, 2026-11-27) as built; StrictMode (V7) checks it | device |
