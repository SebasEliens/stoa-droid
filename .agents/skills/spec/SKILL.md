---
name: spec
description: The specify -> implement -> validate loop for stoa-droid. Specify is the design review where the owner makes every design decision. Use when starting a feature, changing behaviour, or fixing a bug (the bug first becomes a spec example), or when the user asks for /spec.
---

# Specify, implement, validate

Specify is where every design decision is made, by the owner, with you. Implement and validate make none.

## 1. Specify: the design review

### The spec file

- New feature: write `specs/NNN-<slug>.md` (next free number) in the shape of `specs/001-ask-the-stoics.md`:
  status, goal, out of scope, acceptance examples, edge cases, device questions, decisions.
- Bug or behaviour change: add or edit an example in the spec that owns the behaviour.
- Status line: `Status: draft` while specifying, `Status: approved` when the owner OKs it, `Status: implemented`
  when done. Validate skips the test check for draft specs, and fails an `open` decision in any other spec.
- Examples are a table: `| SNNN-n | Given | When | Then |`, describing what the user or caller can observe,
  not how it is built.
- Edge cases: map each to an example, or write one line saying why it does not apply:
  - no matching passage; empty, very long or non-English input
  - no model on the phone; model fails to load; slow first load
  - model output: invented quote, unknown passage id, empty, malformed, unfinished
  - one philosopher vs all three
  - offline: the app works with no network; only a model download may use it (owner, 2026-11-27)

### Device questions

Answer each under `## Device questions`, or write `not applicable` and why:

- **Lifecycle**: what survives rotation, backgrounding, process death and force-stop? What is saved, where, when?
- **Memory**: peak RAM added on the phone; what happens when the low-memory killer takes the process.
  Voice needs headroom later.
- **Threads**: what runs off the main thread, and what cancels it when the user leaves.
- **Hardware**: GPU vs CPU, thermal throttling, a slow or failed model load.
- **Storage**: what goes to disk, its size, what a crash in the middle of a write leaves behind.
- **Data**: where data lives, how its schema changes later.
- **Privacy**: what leaves the app (only model downloads may use the network, and nothing is sent), which
  permissions it needs.

### Decisions

Every design choice is a row under `## Decisions`:

| ID | Area | Question | Options and trade-offs | Choice and why | Verified by |
|---|---|---|---|---|---|
| D004-1 | lifecycle | Keep the chat after process death? | a) in memory only: simple, lost when killed. b) `SavedStateHandle`: survives, ~1 MB cap. c) file: survives force-stop, needs a write policy. Recommend b. | **b** (owner, 2026-11-27) losing the chat after a phone call is the bug users would notice | S004-3 |

- `D<NNN>-n`, with `NNN` the spec number. Area is one of: lifecycle, memory, threads, hardware, storage, data,
  privacy, tooling.
- You write the question, the options with their trade-offs, and your recommendation.
- The choice is the owner's: `**choice** (owner, <date>) reason`, in their words. Until they decide, the cell
  starts with `open`.
- Verified by: one or more, comma-separated (`S004-3, eval`), each a spec example id, `eval` (the fake-LLM golden
  set CI runs), `eval-real`, `bench` or `device`. Prefer an example id or `eval`, because validate runs them.

### How you behave in specify

The owner is learning Android. Teach while you ask.

- Before each question, explain the Android mechanism behind it in plain words (what a configuration change does
  to an Activity, what the low-memory killer is, why the main thread must not block).
- Ask the owner to predict the behaviour before you tell them: "the app is in the background and Android kills
  the process; what is on screen when you come back?" Then say what really happens.
- Push back when a requirement is impossible, vague or contradicts itself, and say why. Never agree by default;
  if the owner's choice has a cost, name it once before writing it down.
- Show the spec and wait for the owner's OK before writing code. Set `Status: approved` only then. A small bug
  fix needs one example, but still show it.

## 2. Implement

- Never add or change a `D` row. A new design question means: add a row whose choice starts with `open`, stop,
  and report it. Do not pick an option yourself, not even the recommended one.
- For each example, write a test whose name starts with its id, e.g. `` `S004-2 empty question shows a hint` ``.
  Put it at the lowest level that can show the behaviour: a `core:*` JVM test for logic, an `app/src/test`
  Robolectric test (like `ChatScreenTest`) for anything the user sees.
- Run it and see it fail for the right reason. Then write the code.
- When the feature creates a new kind of input or model output, add it to `RobustnessTest`.

## 3. Validate

- `scripts/validate.sh` must pass. In Claude Code a Stop hook enforces it. It checks every example has a test
  (draft specs excepted) and every `D` row has a valid area, a real verifier and, outside drafts, no `open` choice.
- Report per example id and per `D` row, plus what could not be checked here: run `/eval -Preal`, `/bench` or the
  device check for rows verified by `eval-real`, `bench` or `device`, or say they were not run.
  `device` has no script yet: check it by hand on the phone.
- Before opening a PR, run the `reviewer` subagent (`.agents/agents/reviewer.md`) on the branch with fresh
  context, giving it the spec. Fix or answer every finding it reports, in the PR body if you keep the code.
- Set the spec's status to `implemented`.
