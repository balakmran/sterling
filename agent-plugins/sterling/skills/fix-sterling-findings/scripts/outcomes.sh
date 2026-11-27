#!/usr/bin/env bash
# Runs ./gradlew with a fixed set of tasks and flags, and records every task's outcome in
# build/sterling-agent/outcomes.json: {"exitCode": N, "tasks": [{"path": ..., "outcome": ...}]}.
# Gradle's output is also saved to build/sterling-agent/gradle.log.
# Outcomes: SUCCESS, UP_TO_DATE, FROM_CACHE, SKIPPED, NO_SOURCE, FAILED.
#
# Usage: outcomes.sh <task or flag>... (run from the project root)
# Only Sterling's tasks and a few others are accepted, so running this script never runs
# arbitrary Gradle code; anything else is rejected with exit code 2.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

allowed() {
  case "$1" in
    stlFmt | stlFix | stlFmtCheck | stlLint | stlSec | stlCov | stlCheck | stlInfo | --json) return 0 ;;
    compileJava | compileTestJava | test | jacocoTestReport | --continue | -q) return 0 ;;
    -Psterling.profile=lenient | -Psterling.profile=standard | -Psterling.profile=strict) return 0 ;;
    :*:stl* | :*:compileJava | :*:compileTestJava | :*:test | :*:jacocoTestReport)
      # A task in one module: the path must be plain module names.
      [[ "$1" =~ ^(:[A-Za-z0-9_.-]+)+$ ]] && return 0 ;;
  esac
  return 1
}

if [[ $# -eq 0 ]]; then
  echo "usage: outcomes.sh <task or flag>..." >&2
  exit 2
fi
for arg in "$@"; do
  if ! allowed "$arg"; then
    echo "outcomes.sh: '$arg' is not allowed; run ./gradlew directly instead." >&2
    exit 2
  fi
done
if [[ ! -x ./gradlew ]]; then
  echo "outcomes.sh: no ./gradlew here; run from the project root." >&2
  exit 2
fi

out="build/sterling-agent/outcomes.json"
log="build/sterling-agent/gradle.log"
mkdir -p build/sterling-agent
rm -f "$out"
set +e
# The log is for findings.sh, which reads javac's diagnostics from it.
./gradlew --init-script "$script_dir/outcomes.init.gradle.kts" "$@" 2>&1 | tee "$log"
code=${PIPESTATUS[0]}
set -e

tasks="[]"
[[ -f "$out" ]] && tasks="$(cat "$out")"
printf '{"exitCode":%d,"tasks":%s}\n' "$code" "$tasks" >"$out"
echo "outcomes: $out (exit code $code)"
exit 0
