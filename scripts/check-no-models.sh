#!/usr/bin/env bash
# Fails if model weights or files over 20 MB are staged (default, for pre-commit) or tracked (--all, for CI).
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
if [[ ${1:-} == --all ]]; then files=$(git ls-files); else files=$(git diff --cached --name-only --diff-filter=AM); fi
bad=0
while IFS= read -r f; do
  if [[ -z $f || ! -f $f ]]; then continue; fi
  case $f in
    *.litertlm | *.task | *.tflite | *.onnx | *.gguf | *.safetensors) echo "Model file must not be committed: $f" >&2; bad=1 ;;
  esac
  if (($(wc -c <"$f") > 20971520)); then echo "File over 20 MB must not be committed: $f" >&2; bad=1; fi
done <<<"$files"
exit $bad
