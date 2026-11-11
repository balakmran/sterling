#!/usr/bin/env bash
# Stop hook: runs spotlessApply when Java or build-script files have uncommitted changes.
# A failure (usually a syntax error) goes back to Claude once; stop_hook_active prevents a loop.
set -uo pipefail

input=$(cat)
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

if [[ -z "$(git status --porcelain -- '*.java' '*.gradle.kts')" ]]; then
  exit 0
fi

if output=$(./gradlew spotlessApply -q 2>&1); then
  exit 0
fi

if [[ "$(jq -r '.stop_hook_active // false' <<<"$input")" == "true" ]]; then
  exit 0
fi
echo "spotlessApply failed; fix the source so it formats:" >&2
tail -n 30 <<<"$output" >&2
exit 2
