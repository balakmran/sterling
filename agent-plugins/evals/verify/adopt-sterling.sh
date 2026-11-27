#!/usr/bin/env bash
# Checks an adopt-sterling run: verify/adopt-sterling.sh <project> <fixture> <style> <version>
# shellcheck disable=SC2001,SC2086
set -uo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
# shellcheck source-path=SCRIPTDIR
source "$here/lib.sh"

project=$1 fixture=$2 style=$3 version=$4
cd "$project" || exit 2
style_upper="$(tr '[:lower:]' '[:upper:]' <<<"$style")"
initial="$(initial_commit)"
subjects="$(agent_subjects)"
echo "commits made:"
sed 's/^/  /' <<<"$subjects"

case "$fixture" in
  unsupported/*)
    check "no commit was made" test "$(git rev-list --count HEAD)" = 1
    check "no file changed" test -z "$(git status --porcelain)"
    transcript="$project/../transcript.jsonl"
    if [[ -f "$transcript" ]]; then
      case "$fixture" in
        unsupported/gradle-8) pattern='Gradle (9|9\.0)' ;;
        *) pattern='(21|toolchain)' ;;
      esac
      check "the report names what to upgrade to" grep -qE "$pattern" "$transcript"
    fi
    finish
    exit
    ;;
esac

build_files="$(git ls-files '*.gradle.kts' '*.gradle' '*.properties' '*.toml' | grep -v '^gradle/wrapper/' | tr '\n' ' ')"
all_build_text="$(for f in $build_files; do cat "$f"; echo; done)"
check "Sterling $version is applied" grep -q "dev.balakmran.sterling" <<<"$all_build_text"
check "at the skill's version" grep -qF "$version" <<<"$all_build_text"
common_checks

info="$(stl_info)"
check "stlInfo reports Sterling" test -n "$info"
first="$(head -1 <<<"$info")"
check "format.style is $style_upper" test "$(json_string "$first" style)" = "$style_upper"

hook_ok=false
for f in .pre-commit-config.yaml .husky/pre-commit lefthook.yml; do
  [[ -f "$f" ]] && grep -q stlFmtCheck "$f" && hook_ok=true
done
check "a pre-commit hook runs stlFmtCheck" $hook_ok

if [[ "$fixture" == greenfield ]]; then
  check "stlFmtCheck succeeds" ./gradlew --console=plain -q stlFmtCheck
  check "the profile is STANDARD (no sterling.profile property)" \
    bash -c '! grep -qs "sterling.profile" gradle.properties'
  finish
  exit
fi

# --- Existing project.
check "commit 1 adds Sterling" grep -qi "add Sterling" <<<"$(head -1 <<<"$subjects")"
reformat_sha="$(git log --reverse --format='%H %s' "$initial..HEAD" | grep -E ' style: reformat' | head -1 | cut -d' ' -f1)"
if [[ -n "$reformat_sha" ]]; then
  ok "a reformat commit exists"
  check "the reformat commit touches only Java files" \
    bash -c "! git diff-tree --no-commit-id --name-only -r $reformat_sha | grep -v '\\.java\$' | grep -q ."
  # The reformat equals its parent with stlFmt applied.
  tmp="$(mktemp -d)"
  git worktree add -q --detach "$tmp/wt" "$reformat_sha^" 2>/dev/null
  # The build's own plugin repository points at the eval's throwaway repository.
  if (cd "$tmp/wt" && ./gradlew --console=plain -q stlFmt >/dev/null 2>&1 &&
    git add -A >/dev/null && test "$(git write-tree)" = "$(git rev-parse "$reformat_sha^{tree}")"); then
    ok "the reformat is exactly what stlFmt produces"
  else
    bad "the reformat is not exactly what stlFmt produces"
  fi
  git worktree remove --force "$tmp/wt" 2>/dev/null
  check "its SHA is in .git-blame-ignore-revs" grep -q "$reformat_sha" .git-blame-ignore-revs
  # The stlFix commit is the one other that may change sources: Error Prone's own fixes.
  stlfix_sha="$(git log --reverse --format='%H %s' "$initial..HEAD" | grep -E ' fix: apply Error Prone' | head -1 | cut -d' ' -f1)"
  if [[ -n "$stlfix_sha" ]]; then
    ok "a stlFix commit exists"
    check "the stlFix commit touches only Java files" \
      bash -c "! git diff-tree --no-commit-id --name-only -r $stlfix_sha | grep -v '\\.java\$' | grep -q ."
    check "a second stlFix changes nothing" \
      bash -c "./gradlew --console=plain -q stlFix >/dev/null 2>&1 && test -z \"\$(git status --porcelain)\""
    git checkout -q -- . 2>/dev/null
  elif [[ "$fixture" == brownfield-service ]]; then
    # Receipt.toString() lacks @Override, which stlFix patches.
    bad "no stlFix commit, though the fixture has a method missing @Override"
  fi
  others="$(git log --format=%H "$initial..HEAD" | grep -v "$reformat_sha" | grep -v "${stlfix_sha:-no-such-commit}")"
  src_touched=false
  for sha in $others; do
    git diff-tree --no-commit-id --name-only -r "$sha" | grep -vE '^(build-logic|buildSrc)/' | grep -qE '(^|/)src/' && src_touched=true
  done
  check "no other commit touches src/ (besides the reformat and stlFix)" test "$src_touched" = false
else
  # Fine when the sources were already formatted, or format.ratchetFrom was chosen.
  check "no reformat commit, so stlFmtCheck passes as it is" ./gradlew --console=plain -q stlFmtCheck
  check "and no blame-ignore file was made" test ! -f .git-blame-ignore-revs
fi

for pattern in 'id\("com.github.spotbugs"\)' 'googleJavaFormat\(' 'removeUnusedImports' 'trimTrailingWhitespace' \
  'endWithNewline' 'NullAway:' 'disableAllChecks' 'violationRules' 'error_prone_core' 'com.uber.nullaway'; do
  check "removed from the build: $pattern" bash -c "! grep -rEq '$pattern' $build_files"
done

# --- Coverage thresholds equal each module's measured floor, set in the module's own script.
./gradlew --console=plain -q test jacocoTestReport -Psterling.profile=standard >/dev/null 2>&1
while IFS= read -r line; do
  [[ -n "$line" ]] || continue
  module="$(json_project "$line")"
  dir="${module#:}"
  dir="${dir//://}"
  report="$(grep -oE '"coverage":\{[^}]*"reports":\{"xml":"[^"]*"' <<<"$line" | grep -oE '"xml":"[^"]*"' | cut -d'"' -f4)"
  [[ -n "$report" ]] || continue
  measured="$("$here/../../sterling/skills/adopt-sterling/scripts/coverage.sh" "${dir:+$dir/}$report" 2>/dev/null)"
  want_i="$(json_number "$measured" instruction)"
  want_b="$(json_number "$measured" branch)"
  check "$module: instruction threshold is $want_i" test "$(json_number "$line" instruction)" = "$want_i"
  check "$module: branch threshold is $want_b" test "$(json_number "$line" branch)" = "$want_b"
  script="${dir:+$dir/}build.gradle.kts"
  check "$module: thresholds are set in $script" grep -q "instruction" "$script"
done <<<"$info"

# --- The profile is STANDARD if and only if the gate passes under it.
if ./gradlew --console=plain -q stlCheck -Psterling.profile=standard >/dev/null 2>&1; then gate=pass; else gate=fail; fi
profile="$(json_string "$first" profileProperty)"
profile_resolved="$(json_string "$first" profile)"
echo "stlCheck under STANDARD: $gate; profile now: $profile_resolved (property: ${profile:-none})"
if [[ "$gate" == pass ]]; then
  check "the profile is STANDARD, since the gate passes" test "$profile_resolved" = STANDARD
else
  check "the profile is LENIENT, since the gate fails under STANDARD" test "$profile_resolved" = LENIENT
fi
finish
