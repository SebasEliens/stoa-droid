---
name: spec
description: The specify -> implement -> validate loop for stoa-droid. Use when starting a feature, changing behaviour, or fixing a bug (the bug first becomes a spec example), or when the user asks for /spec.
---

# Specify, implement, validate

## 1. Specify

- New feature: write `specs/NNN-<slug>.md` (next free number) in the shape of `specs/001-ask-the-stoics.md`:
  status, goal, out of scope, acceptance examples, edge cases.
- Bug or behaviour change: add or edit an example in the spec that owns the behaviour.
- Examples are a table: `| SNNN-n | Given | When | Then |`, describing what the user or caller can observe,
  not how it is built.
- Go through every edge case below. Map each to an example, or write one line saying why it does not apply:
  - no matching passage; empty, very long or non-English input
  - no model on the phone; model fails to load; slow first load
  - model output: invented quote, unknown passage id, empty, malformed, unfinished
  - one philosopher vs all three
  - rotation, app in the background, process death
  - offline (the app must never need the network)
  - memory: does it add RAM on the phone? Voice needs headroom later.
- Show the spec to the user and wait for their OK before writing code. A small bug fix needs one example, but
  still show it.

## 2. Implement

- For each example, write a test whose name starts with its id, e.g. `` `S004-2 empty question shows a hint` ``.
  Put it at the lowest level that can show the behaviour: a `core:*` JVM test for logic, an `app/src/test`
  Robolectric test (like `ChatScreenTest`) for anything the user sees.
- Run it and see it fail for the right reason. Then write the code.
- When the feature creates a new kind of input or model output, add it to `RobustnessTest`.

## 3. Validate

- `scripts/validate.sh` must pass. In Claude Code a Stop hook enforces it.
- Report per example id, plus what could not be checked here: run `/eval -Preal` or `/bench` when the change
  touches the model or its prompts, or say they were not run.
- Set the spec's status to `implemented`.
