# stoa-droid

Android app for talking with Marcus Aurelius, Seneca and Epictetus. Everything runs on the phone: Gemma via
LiteRT-LM, retrieval over public-domain translations, and quotes copied verbatim from the texts.
Target: Fairphone (Gen 6), minSdk 33. Voice (whisper.cpp + local TTS) comes later, so keep RAM headroom.

## Commands

| What | Command |
|---|---|
| **Validate** (everything CI runs) | `scripts/validate.sh` |
| Unit + UI tests | `./gradlew test` |
| Lint + unit tests | `./gradlew check` |
| Eval: golden set, fake LLM (CI runs this) | `./gradlew :tools:eval:run` |
| Eval with a real model on this machine | `./gradlew :tools:eval:run -Preal=/path/model.litertlm` |
| Device benchmark (phone on USB) | `scripts/bench.sh /path/model.litertlm [gpu\|cpu]` |
| Device journey tests, FakeLlm (phone on USB) | `scripts/device-check.sh` |
| Format | `./gradlew ktlintFormat` |
| Install on phone | `./gradlew :app:installDebug` |

Once per clone: `git config core.hooksPath .githooks` (blocks committing model files).
Needs JDK 21 (Gradle selects it via `gradle/gradle-daemon-jvm.properties`) and the Android SDK with platform 37.

## Modules

- `core:data`: `Passage`, `Philosopher`, `Corpus` (loads `corpus/passages.jsonl`). Pure JVM.
- `core:llm`: `Llm` interface, `FakeLlm`, `LiteRtLlm`. Compiled against `litertlm-jvm`; the app adds
  `litertlm-android` (same classes). Pure JVM.
- `core:rag`: `Retriever` (BM25), `Prompts`, quote verification (`findExcerpt`, `parse`), `AnswerPipeline`. Pure JVM.
- `tools:eval`: golden-set eval on the desktop JVM. Cases in `eval/golden.jsonl`, baseline in `metrics/eval.json`.
- `app`: Compose UI, `ChatViewModel`, manual DI in `StoaApp.kt`; debug-only `BenchActivity` for `scripts/bench.sh`.
  `app/src/test`: Robolectric tests that drive the real screen on the JVM.
- `specs/`: one file per feature, with acceptance examples `SNNN-n`.
- `corpus/`: `passages.jsonl`, shipped as APK assets. Today an 80-passage fixture cut from Long's Meditations and
  Encheiridion and Gummere's Seneca; to be replaced by a Kotlin ingest tool that builds the full corpus.

## Rules

- **Quote rule.** Displayed quote text always comes from `findExcerpt`, a slice of the retrieved passage, never
  from model output. Unverifiable quotes are dropped and counted. `QuotesTest` guards this; never weaken it.
- Model output format: prose lines, and quotes on their own line as `> [passage-id] exact words`.
  Change `Prompts` and `parse` together.
- **Never commit model weights** (`*.litertlm`, `*.task`, `*.tflite`, `*.onnx`, `*.gguf`) or any file over 20 MB.
  Models live outside the repo; on the phone in `/sdcard/Android/data/dev.stoa.droid/files/models/`.
- Keep `core:*` free of Android dependencies so tests and eval run on the JVM.
- UI work and tests use `FakeLlm`. Only the app at runtime, `eval -Preal` and the benchmark load real weights.
- No DI framework, Room or Navigation until a feature needs them. Versions live only in `gradle/libs.versions.toml`.

## Development loop: specify, implement, validate

Every feature and bug fix follows the `spec` skill (`.agents/skills/spec/SKILL.md`):

1. **Specify** is the design review: in `specs/NNN-*.md`, acceptance examples `SNNN-n`, the edge cases, the
   device questions and a decision row `DNNN-n` per design choice. You explain and recommend; the owner chooses.
   `Status: draft` until the owner OKs it, then `approved`.
2. **Implement**: a test named after each example id, seen failing first; then the code. Never add or change a
   `D` row: a new design question becomes an `open` row, then stop and report it.
3. **Validate**: `scripts/validate.sh` checks for model files, spec examples have tests and `D` rows are decided
   and verifiable, then runs ktlint, detekt, Android lint, unit tests, Robolectric UI tests and the eval. No
   metric may drop below `metrics/eval.json`; when one improves, copy `build/reports/eval.json` over it in the
   same commit. Never lower the baseline or edit `eval/golden.jsonl` just to pass. A commit that lowers
   `metrics/eval.json` or its case count needs a `Baseline-lowered: <reason>` trailer.

Not covered by validate, so run them or say they were not run: `scripts/bench.sh` on the phone for model,
prompt-length or performance changes, and the eval with `-Preal` for prompt changes.

## MVP is done when

- Chat works offline on the Fairphone with streamed answers.
- Every displayed quote is verbatim (guaranteed by code).
- Eval with the real model: hit@8 >= 0.8 and citation accuracy >= 0.9.
- Benchmark on the Fairphone: time to first token < 3 s, decode >= 8 tokens/s, peak RSS <= 2.5 GB.
