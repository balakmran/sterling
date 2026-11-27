#!/usr/bin/env bash
# Checks an upgrade-sterling run: verify/upgrade-sterling.sh <project> <fixture> <style> <version>
# shellcheck disable=SC2001,SC2086
set -uo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
# shellcheck source-path=SCRIPTDIR
source "$here/lib.sh"

project=$1 version=$4
cd "$project" || exit 2
initial="$(initial_commit)"
subjects="$(agent_subjects)"
echo "commits made:"
sed 's/^/  /' <<<"$subjects"
common_checks

build_files="$(git ls-files '*.gradle.kts' '*.gradle' '*.properties' '*.toml' | grep -v '^gradle/wrapper/' | tr '\n' ' ')"
check "the build applies Sterling $version" grep -qE "dev.balakmran.sterling.*\"$version\"" $build_files
check "no 0.2.0 is left" bash -c "! grep -q 'dev.balakmran.sterling.*\"0\\.2\\.0\"' $build_files"
check "the first commit is the upgrade" grep -qi "upgrade Sterling" <<<"$(head -1 <<<"$subjects")"
check "only the version changed in the build files" \
  bash -c "! git diff -U0 $initial HEAD -- $build_files | grep -E '^[+-][^+-]' | grep -qv 'dev.balakmran.sterling'"
check "format.formatAnnotations isn't set to keep the old layout" \
  bash -c "! grep -rq 'formatAnnotations' $build_files"

# 0.3.0 formats as google-java-format does, so a file 0.2.0 joined fails stlFmtCheck: the upgrade
# needs a reformat commit, hidden from git blame, that changes nothing but Java files.
reformat="$(git log --format='%H %s' "$initial..HEAD" | grep -i 'reformat' | head -1 | cut -d' ' -f1)"
check "a reformat commit follows, since stlFmtCheck failed after the bump" test -n "$reformat"
if [[ -n "$reformat" ]]; then
  check "the reformat commit changes only Java files" \
    test -z "$(git diff --name-only "$reformat^" "$reformat" | grep -v '\.java$')"
  check "the reformat commit changes the request class" \
    bash -c "git diff --name-only $reformat^ $reformat | grep -q CreateStockRequest.java"
  check "the reformat is listed in .git-blame-ignore-revs" grep -q "$reformat" .git-blame-ignore-revs
fi
check "no test changed" test -z "$(git diff --name-only "$initial" HEAD -- src/test)"
check "stlFmtCheck passes" ./gradlew --console=plain -q stlFmtCheck

info="$(stl_info)"
check "stlInfo reports Sterling $version" test "$(json_string "$(head -1 <<<"$info")" sterling)" = "$version"
if ./gradlew --console=plain -q stlCheck >/dev/null 2>&1; then echo "info: stlCheck passes"; else echo "info: stlCheck fails"; fi
finish
