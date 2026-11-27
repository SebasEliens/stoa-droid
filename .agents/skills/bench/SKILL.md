---
name: bench
description: Benchmark the on-device model on the attached phone (time to first token, prefill/decode tokens per second, peak RAM) and compare with the MVP targets. Use after changing LiteRtLlm, prompt length, the model or its backend, or when the user asks for /bench.
---

# Bench

1. `adb get-state` must print `device`. If not, stop and ask the user to plug in the phone with USB debugging on.
   Never estimate numbers without a run.
2. Ask the user which model file to use if it is not known. Models stay outside the repo.
3. Run `scripts/bench.sh <model.litertlm> [gpu|cpu]`. It installs the debug build, pushes the model once,
   and writes one JSON line per question to `build/reports/bench.jsonl`.
4. Report per question: `ttftS`, `decodeTps`, `peakRssMb`, plus `loadS` once, against the MVP targets:
   TTFT < 3 s, decode >= 8 tokens/s, peak RSS <= 2500 MB. Compare with `metrics/bench.jsonl` if it exists.
5. Save a new baseline (`cp build/reports/bench.jsonl metrics/bench.jsonl`) only when the user agrees.
