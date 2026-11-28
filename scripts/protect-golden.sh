#!/usr/bin/env bash
# Claude Code PreToolUse hook (Edit|Write|MultiEdit): eval/golden.jsonl is owner-only, so agents cannot edit it.
# Reads the hook JSON on stdin. Exit 2 = blocked; anything else lets the tool run.
# Known gap: a write through Bash (sed, echo >>) does not pass through this hook; the reviewer flags those diffs.
set -uo pipefail
input=$(cat)
if ! target=$(jq -er '.tool_input.file_path // .tool_input.notebook_path // ""' <<<"$input" 2>/dev/null); then
  echo "protect-golden: could not parse the hook input as JSON; not blocking" >&2
  exit 0
fi
[[ -n $target ]] || exit 0

# Relative paths are relative to the session's cwd (from the hook JSON), else this process's cwd.
cwd=$(jq -r '.cwd // empty' <<<"$input" 2>/dev/null)
[[ $target == /* ]] || target="${cwd:-$PWD}/$target"
# Resolve symlinks and ./ or ../ in the directory part; the file itself may not exist yet.
dir=$(cd "$(dirname "$target")" 2>/dev/null && pwd -P) || exit 0
target="$dir/$(basename "$target")"
[[ -L $target ]] && target=$(readlink -f "$target" 2>/dev/null || echo "$target")

# Every checkout of this repo (main and worktrees) counts. Compared case-insensitively: macOS disks usually are.
lower() { tr '[:upper:]' '[:lower:]' <<<"$1"; }
here=$(cd "$(dirname "$0")/.." && pwd -P)
while IFS= read -r root; do
  root=$(cd "$root" 2>/dev/null && pwd -P) || continue
  if [[ $(lower "$target") == "$(lower "$root/eval/golden.jsonl")" ]]; then
    echo "eval/golden.jsonl is owner-only: propose the case in chat; the owner adds it." >&2
    exit 2
  fi
done < <({ echo "$here"; git -C "$here" worktree list --porcelain 2>/dev/null | sed -n 's/^worktree //p'; })
exit 0
