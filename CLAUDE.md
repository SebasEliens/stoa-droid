@AGENTS.md

## Claude Code only

- After every Edit/Write of a `.kt`/`.kts` file a hook runs `scripts/post-edit.sh` (ktlintFormat + that module's
  unit tests). If it reports a failure, fix it before doing anything else.
- Skills `/eval` and `/bench` live in `.agents/skills` (linked from `.claude/skills`).
