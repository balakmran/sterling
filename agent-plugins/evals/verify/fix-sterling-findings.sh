#!/usr/bin/env bash
# Checks a fix-sterling-findings run: verify/fix-sterling-findings.sh <project> <fixture> <style> <version>
# shellcheck disable=SC2001,SC2086
set -uo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
# shellcheck source-path=SCRIPTDIR
source "$here/lib.sh"

project=$1
cd "$project" || exit 2
initial="$(initial_commit)"
echo "commits made:"
agent_subjects | sed 's/^/  /'
common_checks

check "stlCheck succeeds" ./gradlew --console=plain -q stlCheck
# Cleaner.toString() lacks @Override, which stlFix patches, in a commit of its own.
check "Error Prone's fixes were applied with stlFix, in their own commit" \
  bash -c "git log --format=%s $initial..HEAD | grep -qi 'stlFix'"
check "the build, properties and filter files are unchanged" \
  test -z "$(git diff --name-only "$initial" HEAD -- '*.gradle.kts' '*.gradle' '*.properties' '*.toml' config)"
check "no existing test was modified or deleted" \
  test -z "$(git diff --diff-filter=MD --name-only "$initial" HEAD -- 'src/test')"
check "tests were added" test -n "$(git diff --diff-filter=A --name-only "$initial" HEAD -- 'src/test')"

# Every added @SuppressWarnings has a comment line above it.
suppressions="$(git diff -U1 "$initial" HEAD -- 'src/main' 'src/test' | grep -B1 '^+.*@SuppressWarnings' | grep -v '^--' || true)"
if [[ -z "$suppressions" ]]; then
  ok "no suppressions were added"
else
  echo "suppressions added:"
  sed 's/^/  /' <<<"$suppressions"
  check "each suppression has a reason comment" \
    bash -c "! git diff -U1 $initial HEAD -- src/main src/test | grep -B1 '^+.*@SuppressWarnings' | grep -v '^--' | awk 'NR%2==1 && \$0 !~ /^[+ ] *\\/\\//{bad=1} END{exit !bad}'"
fi
finish
