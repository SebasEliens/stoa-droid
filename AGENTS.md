# stoa-droid

Android app for talking with Marcus Aurelius, Seneca and Epictetus. Everything runs on the phone: Gemma via
LiteRT-LM, retrieval over public-domain translations, and quotes copied verbatim from the texts.
Target: Fairphone (Gen 6), minSdk 33. Voice (whisper.cpp + local TTS) comes later, so keep RAM headroom.

## Commands

| What | Command |
|---|---|
| Unit tests | `./gradlew test` |
| Lint + unit tests | `./gradlew check` |
| Eval: golden set, fake LLM (CI runs this) | `./gradlew :tools:eval:run` |
| Eval with a real model on this machine | `./gradlew :tools:eval:run -Preal=/path/model.litertlm` |
| Device benchmark (phone on USB) | `scripts/bench.sh /path/model.litertlm [gpu\|cpu]` |
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

## Verify a change

1. `./gradlew check` (ktlint, detekt, unit tests).
2. `./gradlew :tools:eval:run`: no metric may drop below `metrics/eval.json`. If one improves, copy
   `build/reports/eval.json` over the baseline in the same commit. Never lower the baseline or edit
   `eval/golden.jsonl` just to pass.
3. Changes to `LiteRtLlm`, prompt length or anything performance-related: run `scripts/bench.sh` on the phone,
   or state that it was not run.
4. UI changes: install on a phone or emulator and look at it.

## MVP is done when

- Chat works offline on the Fairphone with streamed answers.
- Every displayed quote is verbatim (guaranteed by code).
- Eval with the real model: hit@8 >= 0.8 and citation accuracy >= 0.9.
- Benchmark on the Fairphone: time to first token < 3 s, decode >= 8 tokens/s, peak RSS <= 2.5 GB.
