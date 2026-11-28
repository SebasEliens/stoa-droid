# Plan: validation baked into development

Status: approved 27 Nov 2026. V0 is done.

## What changes

Every change to stoa-droid ends in a check that a machine runs. Each kind of check covers one risk:

| Check | Catches | Runs |
|---|---|---|
| Unit + Robolectric tests, ktlint, detekt, Android lint | logic and UI bugs on the JVM | every edit (hook), every stop (hook), CI |
| Fake-LLM eval with history ratchet | retrieval and quote-parsing regressions, lowered baselines | every stop, CI |
| Permission allowlist test | new permissions slipped in by a dependency | every stop, CI |
| Reviewer subagent | gaps between the spec and the diff | before every PR |
| Instrumented journey on the Fairphone + StrictMode | real-device crashes, main-thread I/O, leaked closeables | before a PR that touches `app/` or `core:llm` |
| Device benchmark gate | TTFT, decode speed, RAM regressions | before a PR that touches the model, prompts or `LiteRtLlm` |
| Real-model eval gate (greedy) | answer quality regressions with real Gemma | before a PR that touches prompts, retrieval or the model |

Your decisions (27 Nov 2026): bench fails on >10% drift from the Fairphone baseline or any MVP limit; StrictMode
crashes debug builds; the permission list is pinned by a test; the real eval is greedy with a committed baseline;
device checks are instrumented tests; agents cannot edit `eval/golden.jsonl`; `main` is protected and changes go through PRs.

## Landing order and dependencies

```
V0 main + protection
 └── V1 specify = design review (spec skill, spec format, validate check)
      ├── V2 ratchet + permission allowlist   (independent)
      ├── V3 golden-set hook                  (independent)
      ├── V4 reviewer subagent + strict lint  (independent)
      ├── V5 real-model eval gate             (independent, needs a model on the Mac)
      └── V6 device journey ── V7 StrictMode ── V8 bench gate   (serial: one phone)
                                                      └── V9 orchestrator + workers (needs V1, V4, V6 to V8)
                                                           └── V10 auto-merge on green CI
```

Spec 002, the living documentation site, lands right after V1 (you, 27 Nov), so the timeline is visible while
V2 to V10 are built. It does not touch the phone, so it needs none of the device checks.

V1 lands first, because every later PR's spec uses its format. V2 to V5 branch from `main` and touch disjoint files, apart from one line each in `AGENTS.md` (rebase when needed).
V6 to V8 share the phone and `app/`, so they land one after another.

Review-gated (you review before merge): V0 (GitHub settings), V1 (it defines how you and the agent work), V5 and V8 (you approve the baseline numbers),
V7 (changes how the debug app behaves). Until V10 you merge every PR. After V10, a PR merges itself once every required check is green, and agents still never merge by hand.

---

## V0: `main` branch and protection

- [x] Done 27 Nov 2026: `main` from 399f32a, default branch, protected (PR required, `check` required, no force-push or deletion), fork PRs from any outside contributor need approval.
- [ ] Depends on: nothing. Needs your explicit go, since it changes GitHub settings.
- [ ] Files: none in the repo. GitHub: create `main` from `setup/skeleton` (399f32a), make it the default
      branch, protect it: PR required, CI job `check` must pass, no force-push, no deletion.
- [ ] Change: the repo's lifecycle becomes branch, then PR, then green CI, then your merge.
- [ ] Result: `gh api repos/:owner/stoa-droid/branches/main/protection` shows the rules.
- [ ] Verify (live): a test push straight to `main` is refused; a PR with a failing check cannot merge.

## V1: specify is the design review

The loop stays specify, implement, validate. Specify becomes the phase where every design decision is made, with
you, and with teaching. Implement and validate then run without new design choices.

- [ ] Depends on: V0.
- [ ] Files: `.agents/skills/spec/SKILL.md`, `specs/001-ask-the-stoics.md` (retrofit), `scripts/validate.sh`, `AGENTS.md`.
- [ ] Change, in the spec skill:
  - **Device questions.** Each spec answers these, or says why one does not apply:
    - Lifecycle: what survives rotation, backgrounding, process death and force-stop? What is saved, where, and when?
    - Memory: the RAM it adds at peak, and what happens under the low-memory killer.
    - Threads: what runs off the main thread, and what cancels it when the user leaves.
    - Hardware: GPU vs CPU, thermal throttling, a slow or failed model load.
    - Storage: what goes to disk, its size, and what a crash in the middle of a write leaves behind.
    - Data: where data lives, what leaves the app (nothing over the network), and how the schema changes later.
  - **A decision table in the spec:** `| D<NNN>-n | area | question | options and trade-offs | choice and why | verified by |`, where area is one of lifecycle, memory, threads, hardware, storage, data, privacy or tooling (validate checks it).
    The "choice and why" column is your words. The agent writes the options and trade-offs, and recommends one.
  - **How the agent behaves in specify:**
    - It explains the Android mechanism behind each question in plain words before asking.
    - It asks you to predict behaviour ("the app is in the background and Android kills the process, what is
      on screen when you come back?") before it tells you.
    - It pushes back when a requirement is impossible, vague or contradicts itself, and says why. It does not agree
      by default.
  - **In implement:** if a new design question comes up, the agent stops, adds a `D` row as `open`, and asks.
    It never decides it alone.
- [ ] Change, in `scripts/validate.sh`: every `D` row names, under "verified by", a spec example id that has a test, or
      `bench`, `eval-real` or `device`. A row still marked `open` fails validate.
- [ ] Change, retrofit: spec 001 gets its device questions and `D` rows for the decisions already made (BM25 over
      embeddings, corpus as APK assets, the model on external app storage, CPU fallback, chat lost on process death).
      You confirm or change each one. That is the first real use of the new format.
- [ ] Result: every design decision is written down, chosen by you, and tied to a check.
- [ ] Verify (unit): validate fails on a `D` row with no "verified by" and on an `open` row; it passes for spec 001.
      Verify (live): run `/spec` on a small real feature and check that it asks the device questions before any code.

## V2: eval ratchet and permission allowlist

Already built in the working tree (eval ratchet, `S001-6`). This PR widens `S001-6` from "no INTERNET" to an allowlist.

- [ ] Depends on: V0.
- [ ] Files: `tools/eval/**`, `.github/workflows/ci.yml`, `AGENTS.md`, `specs/001-ask-the-stoics.md`,
      `app/src/test/.../PermissionsTest.kt`.
- [ ] Change: `S001-6` reads "the requested permissions are exactly the approved list, and INTERNET is never on it".
      The approved list today is what LiteRT and WorkManager add: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`,
      `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `<app>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
      Each entry names the library that adds it and why it is accepted. If you approve D002-9, the list lives in
      `app/permissions.yaml`, so the living doc shows exactly what the test enforces.
- [ ] Result: a dependency bump that adds a permission fails CI until you add it to the list.
- [ ] Verify (unit): adding INTERNET to the manifest fails; adding any other permission fails; today's manifest passes.
      Ratchet: lowering `metrics/eval.json` fails and names the commit. Corrupt baseline fails. Both already shown once.

## V3: agents cannot edit the golden set

- [ ] Depends on: V0.
- [ ] Files: `scripts/protect-golden.sh` (new), `.claude/settings.json`, `.agents/skills/eval/SKILL.md`, `AGENTS.md`.
- [ ] Change: a `PreToolUse` hook on `Edit|Write` refuses `eval/golden.jsonl` with "propose the case in chat; the
      owner adds it". The eval skill says the same.
- [ ] Result: an agent can suggest golden cases but cannot write them.
- [ ] Verify (live): in a Claude session, ask it to add a case; the hook blocks the edit. Known gap: a write through
      Bash (`sed`, `echo >>`) gets past it, so the reviewer (V4) also flags any diff to `eval/golden.jsonl`.

## V4: reviewer subagent and strict lint

- [ ] Depends on: V0.
- [ ] Files: `.agents/agents/reviewer.md` (new), `.claude/agents` symlink, `.agents/skills/spec/SKILL.md`,
      `app/build.gradle.kts`, `app/lint-baseline.xml` (new, if there are existing warnings).
- [ ] Change: a read-only reviewer, run with fresh context, that checks the branch diff against its spec. It reports
      only these: code that departs from a `D` row in the spec; a spec example without a test; network use; inference or file I/O on the main thread; an `Llm` or
      `Engine` that is never closed; exact-text asserts on model output; diffs to `eval/golden.jsonl`, `metrics/`,
      or to a removed or weakened test (`QuotesTest` above all). The spec skill runs it before a PR.
      Android lint gets `warningsAsErrors = true`. Existing warnings go into a lint baseline that only shrinks.
- [ ] Result: every PR has one fresh-context review, and a new lint warning fails `check`.
- [ ] Verify (live): run the reviewer on a planted branch, a commit that drops `.use {}` in `LiteRtLlm` and deletes a
      test; it must name both with file and line. Lint: a planted warning fails `./gradlew :app:lint`.

## V5: real-model eval gate

- [ ] Depends on: V0. Needs your model path on the Mac.
- [ ] Files: `core/llm/.../LiteRtLlm.kt` (sampler option), `tools/eval/**`, `metrics/eval-real.json` (new),
      `.agents/skills/eval/SKILL.md`, `AGENTS.md`.
- [ ] Change: `LiteRtLlm` takes an optional sampler config. The app keeps today's default; the eval passes greedy
      (temperature 0, top-k 1). `-Preal` compares with `metrics/eval-real.json` and fails on a drop of more than
      one case's worth (1/cases), using the same ratchet and trailer as the fake baseline.
- [ ] Result: prompt or retrieval changes get a real quality gate on the Mac. CI still runs only the fake eval.
- [ ] Verify (live): two greedy runs on the same commit give identical metrics (proves repeatability first; if not,
      we revisit the tolerance with you). Then a planted prompt break fails the gate.
- [ ] Perf: none; eval wall time is reported.
- [ ] Decision for you when it lands: the first `eval-real.json` numbers become the baseline.

## V6: instrumented journey on the Fairphone

- [x] Depends on: V0.
- [x] Files: `app/src/androidTest/**` (new), `app/build.gradle.kts` (androidTest deps, test runner),
      `gradle/libs.versions.toml`, `scripts/device-check.sh` (new), `.agents/skills/device/SKILL.md` (new),
      `AGENTS.md`.
- [x] Change: Compose UI tests that run on the phone with `FakeLlm`. The journey: launch, ask a question, the
      streamed answer completes, every quote shown appears verbatim in its passage, and rotation keeps the chat.
      `scripts/device-check.sh` checks `adb get-state`, runs `connectedDebugAndroidTest`, and saves a screenshot
      and logcat under `build/reports/device/`. The `/device` skill wraps it.
- [x] Result: "works on the phone" becomes a pass or fail with evidence.
- [ ] Verify (live): green on the Fairphone; a planted crash in `ChatViewModel` turns it red with the stack trace
      in the saved logcat.

## V7: StrictMode in debug builds

- [ ] Depends on: V6 (the journey is what exercises it).
- [ ] Files: `app/src/debug/**` (a debug-only `Application` subclass or initializer), `app/src/debug/AndroidManifest.xml`.
- [ ] Change: debug builds turn on StrictMode with `penaltyDeath`. The thread policy catches disk reads and
      writes and network use on the main thread. The VM policy catches leaked closeables, leaked activities and
      unregistered receivers. Release builds are unchanged.
- [ ] Result: a main-thread file read or a leaked `Engine` crashes the debug app, and V6's journey turns red.
- [ ] Verify (live): the V6 journey still passes. Then plant a main-thread `assets.open` and see it fail.
- [ ] Decision likely to come up: if today's code already violates a policy (the corpus and model load lazily;
      where they first run decides the thread), I will show you the violation and the options before changing
      where anything loads. That choice is resource management, so it is yours.

## V8: device benchmark gate

- [ ] Depends on: V7 (benchmark the code StrictMode has already vetted). Phone attached.
- [ ] Files: `scripts/bench.sh`, `scripts/bench-compare.sh` (new, or a `tools:eval`-style JVM tool), `metrics/bench-fairphone.json`
      (new), `.agents/skills/bench/SKILL.md`, `AGENTS.md`.
- [ ] Change: each question runs 5 times and the median counts. Compare: fail on TTFT, load time or peak RSS more
      than 10% above baseline, or decode tok/s more than 10% below; fail on any MVP limit (TTFT 3 s, decode 8 tok/s,
      RSS 2.5 GB) whatever the baseline. Baseline lowering uses the same `Baseline-lowered:` trailer rule.
- [ ] Also: `bench.sh` fails at once when the phone is asleep or locked, instead of waiting 10 minutes (seen 28 Nov), and
      records `dumpsys meminfo` (file-mapped vs private memory) with the model loaded, for D001-6 and the living doc (S002-11).
- [ ] Result: `/bench` says pass or fail, not only numbers.
- [ ] Verify (perf): first measure the run-to-run spread on the current commit (5 x 5 runs). If the spread is above
      5%, a 10% gate will flake, and I will bring you the numbers to choose a tolerance before recording the
      baseline. Then a planted slowdown (e.g. a much longer prompt) must fail.
- [ ] Decision for you: approve `metrics/bench-fairphone.json` before it is committed.

---

## V9: orchestrator and workers

The main session is the orchestrator. It runs specify with you: it teaches, asks questions and pushes back, and it
owns the spec until you approve it. It then hands implement and validate to workers and reviews what comes back.

- [ ] Depends on: V1 (the spec format workers implement against), V4 (the reviewer), V6 to V8 (device checks).
- [ ] Files: `AGENTS.md` (orchestrator section), `.agents/agents/implementer.md`, `.agents/agents/device-validator.md`
      (new), `.claude/agents` symlink, `scripts/phone-lock.sh` (new), `.worktreeinclude`.
- [ ] Data shape: a worker's brief is one approved spec plus the PR id. Its report is the PR link, each example id
      with its test result, each `D` row with the check that verifies it, and any `open` question it ran into.
- [ ] Change:
  - **`implementer`:** one spec per run, in its own worktree and branch. It writes tests first, then code, runs
    `scripts/validate.sh`, and opens a PR. It may not add or change a `D` row. A design question stops it with an
    `open` row, and the orchestrator brings that question to you.
  - **`device-validator`:** read-only. It runs `/device`, `/bench` and the real eval, and holds the phone lock
    while it does. Only one can touch the phone at a time.
  - **The reviewer (V4)** runs on every worker PR before the orchestrator reports to you.
- [ ] Result: you spend your time in specify; implementation and validation run without you, and every design
      question still reaches you.
- [ ] Verify (live): run a planted spec with a hidden design question through the loop. The implementer must stop
      with an `open` row and not decide it. Two device-validators started together must run one after the other.

## V10: auto-merge on green CI

- [ ] Depends on: V9. Needs your go, since it changes GitHub settings.
- [ ] Problem to solve first: GitHub's CI has no phone and no model, so "green CI" today says nothing about the
      device, the benchmark or the real eval. A PR that touches `app/` or `core:llm` must not merge on unit tests alone.
- [ ] Change: those three checks report to the PR as required checks named `device`, `bench` and `eval-real`.
      They pass automatically when no relevant path changed. A device job that cannot reach the Mac or the phone fails after a timeout
      (you, 27 Nov: to catch a sleeping Mac or an unplugged phone), so a waiting PR does not wait forever. You choose how they run, in specify (see the reply).
      Then the repo enables auto-merge, and the orchestrator runs `gh pr merge --auto --squash` on each worker PR.
- [ ] Result: a PR merges itself once every required check is green, and only then.
- [ ] Verify (live): a docs-only PR merges by itself. A PR that touches `app/` waits until `device` is green, and a
      red `bench` blocks it.

## After this plan

- Golden set growth (20 to 50 or more cases) from error analysis on real-model traces: you label, agents propose.
  This is the next data task once V5 exists.
- Removing LiteRT's WorkManager and foreground-service permissions: only after V6 to V8 can prove the app still works without them.

## Not covered, on purpose

- mobile-mcp, Android CLI, `TASKS.md`: not until the checks above are routine.
- Release signing and Play upload: never by an agent.
