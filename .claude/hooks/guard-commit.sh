#!/usr/bin/env bash
# PreToolUse hook for Bash: blocks a git commit on main or one carrying a Co-Authored-By trailer.
# Exit 2 blocks the command and shows stderr to Claude; anything else lets it run.
set -euo pipefail

command=$(jq -r '.tool_input.command // empty')

# Only git commit, possibly after cd ... && or with -C <dir>.
if ! grep -Eq '(^|[;&|(]|[[:space:]])git([[:space:]]+-C[[:space:]]+[^[:space:]]+)?[[:space:]]+commit([[:space:]]|$)' <<<"$command"; then
  exit 0
fi

# The trailer form, with its colon, so a message that merely mentions it still commits.
if grep -Eqi 'co-authored-by:' <<<"$command"; then
  echo "Blocked: this repo never takes a Co-Authored-By trailer. Commit again without it." >&2
  exit 2
fi

branch=$(git -C "${CLAUDE_PROJECT_DIR:-.}" branch --show-current 2>/dev/null || true)
if [[ "$branch" == "main" ]]; then
  echo "Blocked: main is the trunk and only takes PRs. Create a feat/, fix/, ci/, docs/, build/ or test/ branch in a separate command first, then commit." >&2
  exit 2
fi

exit 0
