#!/usr/bin/env bash
# Claude Code Stop hook: the agent cannot finish while scripts/validate.sh fails. It gives up after 3 blocked
# stops in a row, so a failure the agent cannot fix (e.g. no network) does not loop forever.
set -uo pipefail
session=$(jq -r '.session_id // "unknown"')
count_file="${TMPDIR:-/tmp}/stoa-stop-blocks-$session"
cd "$(git rev-parse --show-toplevel)"
if out=$(scripts/validate.sh --if-changed 2>&1); then
  rm -f "$count_file"
  exit 0
fi
n=$(($(cat "$count_file" 2>/dev/null || echo 0) + 1))
echo "$n" >"$count_file"
((n > 3)) && exit 0
{
  echo "scripts/validate.sh failed (block $n of 3). Fix it before finishing, or tell the user why it cannot pass:"
  echo "$out" | grep -vE '^\s*$|^\* (Try|Get more)|^> Run with' | tail -40
} >&2
exit 2
