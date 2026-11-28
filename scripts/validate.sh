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

is_draft() { grep -qxE 'Status: draft[[:space:]]*' "$1"; }
trim() { sed -E 's/^[[:space:]]+|[[:space:]]+$//g' <<<"$1"; }

echo "== every spec example has a test named after its id (draft specs skipped)"
missing=0
for spec in specs/*.md; do
  is_draft "$spec" && continue
  for id in $(grep -hoE '^\| S[0-9]{3}-[0-9]+ ' "$spec" | tr -d '| '); do
    if ! grep -rqF --include='*.kt' -- "\`$id " app/src/test core tools; then
      echo "No test for $id: name one \`$id ...\`" >&2
      missing=1
    fi
  done
done
((missing == 0))

echo "== every decision has a valid area, a real verifier, and is decided outside drafts"
examples=" $(grep -hoE '^\| S[0-9]{3}-[0-9]+ ' specs/*.md | tr -d '| ' | tr '\n' ' ') "
bad=0
for spec in specs/*.md; do
  while IFS='|' read -r _ id area _ _ choice verified _; do
    id=$(trim "$id") area=$(trim "$area") choice=$(trim "$choice") verified=$(trim "$verified")
    case $area in
      lifecycle | memory | threads | hardware | storage | data | privacy | tooling) ;;
      *) echo "$spec $id: area '$area' is not one of lifecycle, memory, threads, hardware, storage, data, privacy, tooling" >&2; bad=1 ;;
    esac
    [[ -n $verified ]] || { echo "$spec $id: verified by is empty" >&2; bad=1; }
    IFS=',' read -ra checks <<<"$verified"
    for check in "${checks[@]}"; do
      check=$(trim "$check")
      case $check in
        eval | eval-real | bench | device) ;;
        S[0-9][0-9][0-9]-[0-9]*)
          [[ $examples == *" $check "* ]] || { echo "$spec $id: verified by $check, but no spec has that example" >&2; bad=1; } ;;
        *) echo "$spec $id: verified by '$check' is not an S id, eval, eval-real, bench or device" >&2; bad=1 ;;
      esac
    done
    [[ -n $choice ]] || { echo "$spec $id: choice is empty" >&2; bad=1; }
    if ! is_draft "$spec" && [[ ${choice//\*/} == open* ]]; then
      echo "$spec $id: still open in a spec that is not draft; the owner decides it first" >&2
      bad=1
    fi
  done < <(grep -E '^\| D[0-9]{3}-[0-9]+ ' "$spec" || true)
done
((bad == 0))

echo "== lint, unit and UI tests, eval, docs site"
./gradlew -q check :tools:eval:run :tools:docs:run

fingerprint >"$stamp"
echo "validate: passed"
