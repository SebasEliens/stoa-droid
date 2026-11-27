#!/usr/bin/env bash
# Formats and unit-tests the Gradle module that owns an edited Kotlin file.
# Usage: scripts/post-edit.sh <file>, or Claude Code hook JSON on stdin. Exit 2 = failure the agent must fix.
set -uo pipefail
file=${1:-$(jq -r '.tool_input.file_path // empty')}
[[ $file == *.kt || $file == *.kts ]] || exit 0
root=$(git rev-parse --show-toplevel)
rel=${file#"$root"/}
case $rel in
  app/*) module=:app test=testDebugUnitTest ;;
  core/*/* | tools/*/*) module=:$(cut -d/ -f1-2 <<<"$rel" | tr / :) test=test ;;
  *) exit 0 ;;
esac
cd "$root"
if ! out=$(./gradlew -q "$module:ktlintFormat" "$module:$test" 2>&1); then
  echo "$out" | grep -vE '^\s*$|^\* (Try|Get more)|^> Run with' | tail -40 >&2
  exit 2
fi
