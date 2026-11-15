#!/usr/bin/env bash
# Shared helpers for the verify scripts: each check prints "ok: ..." or "FAIL: ...", and the script
# exits 1 when any failed. Sourced, not run.

failures=0

ok() { echo "ok: $*"; }

bad() {
  echo "FAIL: $*"
  failures=$((failures + 1))
}

# check "description" command...: ok when the command succeeds.
check() {
  local description=$1
  shift
  if "$@" >/dev/null 2>&1; then ok "$description"; else bad "$description"; fi
}

# The commit the fixture started from.
initial_commit() { git rev-list --max-parents=0 HEAD | tail -1; }

# Subjects of the commits the agent made, oldest first.
agent_subjects() { git log --reverse --format=%s "$(initial_commit)..HEAD"; }

# Sterling's JSON lines (one per project), from the project's own wrapper.
stl_info() { ./gradlew --console=plain -q stlInfo --json 2>/dev/null; }

# A number from one JSON line: json_number '<line>' instruction
json_number() { grep -oE "\"$2\":[0-9]+" <<<"$1" | head -1 | cut -d: -f2; }

# A string from one JSON line: json_string '<line>' style
json_string() { grep -oE "\"$2\":\"[^\"]*\"" <<<"$1" | head -1 | cut -d'"' -f4; }

# The project path of each stlInfo line, one per module.
json_project() { json_string "$1" project; }

# Fails the script when any check failed.
finish() {
  if ((failures > 0)); then
    echo "$failures check(s) failed"
    exit 1
  fi
  echo "all checks passed"
}

# Verification needs a clean tree to compare with, and no plugin settings written to the project.
common_checks() {
  check "the working tree is clean" test -z "$(git status --porcelain)"
  check "no agent plugin settings in the project" \
    bash -c '! ls .claude/settings.json .github/copilot/settings.json .codex/config.toml 2>/dev/null | grep -q .'
  check "nothing was pushed (no remote)" test -z "$(git remote)"
}
