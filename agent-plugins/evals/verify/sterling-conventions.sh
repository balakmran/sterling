#!/usr/bin/env bash
# Checks a sterling-conventions run: verify/sterling-conventions.sh <project> <fixture> <style> <version>
# The fixture is greenfield with Sterling at STRICT, and the agent adds a small feature.
set -uo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
# shellcheck source-path=SCRIPTDIR
source "$here/lib.sh"

project=$1
cd "$project" || exit 2
initial="$(initial_commit)"

check "main code was added" test -n "$(git status --porcelain -uall -- src/main | grep -E '\.java$' || git diff --name-only "$initial" HEAD -- src/main)"
check "a test was added" test -n "$(git status --porcelain -uall -- src/test | grep -E 'Test\.java$' || git diff --name-only "$initial" HEAD -- src/test)"
check "stlCheck succeeds at STRICT" ./gradlew --console=plain -q stlCheck
finish
