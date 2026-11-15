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
check "no 0.1.0 is left" bash -c "! grep -q 'dev.balakmran.sterling.*\"0\\.1\\.0\"' $build_files"
check "the first commit is the upgrade" grep -qi "upgrade Sterling" <<<"$(head -1 <<<"$subjects")"
check "no reformat commit, since google-java-format didn't change" \
  bash -c "! grep -qi 'reformat' <<<'$subjects'"
check "the Application exclude is removed" bash -c "! grep -rq 'Application\\*' $build_files"
check "CustomContractAnnotations is removed" bash -c "! grep -rq 'CustomContractAnnotations' $build_files"
check "no source file changed" test -z "$(git diff --name-only "$initial" HEAD -- src)"

info="$(stl_info)"
check "stlInfo reports Sterling $version" test "$(json_string "$(head -1 <<<"$info")" sterling)" = "$version"
if ./gradlew --console=plain -q stlCheck >/dev/null 2>&1; then echo "info: stlCheck passes"; else echo "info: stlCheck fails"; fi
finish
