@AGENTS.md

## Claude Code only

- After every Edit/Write of a `.kt`/`.kts` file a hook runs `scripts/post-edit.sh` (ktlintFormat + that module's
  unit tests). If it reports a failure, fix it before doing anything else.
- A Stop hook runs `scripts/validate.sh --if-changed` (via `scripts/claude-stop-hook.sh`) and blocks finishing while
  it fails, up to 3 times in a row.
- Skills `/spec`, `/eval` and `/bench` live in `.agents/skills` (linked from `.claude/skills`).
