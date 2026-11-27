#!/usr/bin/env bash
# The validate step of the specify -> implement -> validate loop. CI and the agent's Stop hook run exactly this.
# Usage: scripts/validate.sh [--if-changed]   --if-changed: skip when nothing changed since the last pass.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
stamp="$(git rev-parse --git-dir)/stoa-validated"

fingerprint() {
  { git rev-parse HEAD; git diff HEAD; git ls-files --others --exclude-standard -z | xargs -0 -r shasum; } | shasum | cut -c1-40
}
if [[ ${1:-} == --if-changed && -f $stamp && $(cat "$stamp") == "$(fingerprint)" ]]; then
  echo "validate: nothing changed since the last pass"
  exit 0
fi

echo "== no model files or files over 20 MB"
scripts/check-no-models.sh --all

echo "== every spec example has a test named after its id"
missing=0
for id in $(grep -hoE '^\| S[0-9]{3}-[0-9]+ ' specs/*.md | tr -d '| '); do
  if ! grep -rqF --include='*.kt' -- "\`$id " app/src/test core tools; then
    echo "No test for $id: name one \`$id ...\`" >&2
    missing=1
  fi
done
((missing == 0))

echo "== lint, unit and UI tests, eval"
./gradlew -q check :tools:eval:run

fingerprint >"$stamp"
echo "validate: passed"
