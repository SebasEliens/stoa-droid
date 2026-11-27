---
name: eval
description: Run the stoa-droid golden-set eval (retrieval hit@8, citation accuracy, exact-quote rate) and compare it with the committed baseline. Use after changing retrieval, prompts, quote parsing or the corpus, or when the user asks for /eval.
---

# Eval

1. Run `./gradlew -q :tools:eval:run` (fake LLM; this is what CI gates on).
   With a real model on this machine: `./gradlew -q :tools:eval:run -Preal=/path/to/model.litertlm`.
   Ask the user for the model path; never copy model files into the repo.
2. Compare `build/reports/eval.json` with `metrics/eval.json` (`git diff --no-index` works).
3. Report in a few lines: each metric old -> new, the two MVP targets (ok / not yet), and the questions
   marked `MISS` or with wrong citations.
4. Fake-mode metrics improved and the change is intended: `cp build/reports/eval.json metrics/eval.json`
   and commit it with the change. Never lower the baseline or edit `eval/golden.jsonl` to make a change pass;
   golden-set edits are their own commit with a reason.
