#!/usr/bin/env bash
# Runs a coding agent headless on a fixture with the Sterling agent plugin loaded, then checks the
# result with verify/<skill>.sh. The agent's prose is never the evidence: the verify script reads
# git history, files and Gradle task outcomes.
#
# Usage: run.sh <agent> <skill> <fixture> [--style google|aosp] [--published] [--sterling-version V]
#                       [--model M] [--keep] [--prepare-only]
#        run.sh --verify <work directory>
#   agent      claude (copilot and codex have no headless runner here yet)
#   skill      adopt-sterling | fix-sterling-findings | upgrade-sterling | sterling-conventions
#   fixture    a directory name under fixtures/, e.g. brownfield-service or unsupported/gradle-8,
#              or the path of any project directory (its settings files may carry the
#              `// @sterling-eval-repo` marker in a plugin repositories block)
#
# By default the run simulates the release: the repository is copied, its version bumped to
# --sterling-version (default 0.2.0) in version.txt, the three manifests and every skill, and the
# Gradle plugin is published to a throwaway file repository the fixture's settings point to. That
# lets a skill apply "its" Sterling version before it is on the Plugin Portal. --published skips
# this and uses the plugin as it is, from the Plugin Portal.
#
# The work directory (project, transcript, report) is printed; it is deleted unless the run fails
# or --keep is given.
#
# --prepare-only builds the project and writes the prompt to <work>/prompt.txt, then stops, so
# another harness can play the agent: load <work>/plugin (printed) as the agent plugin, give it the
# prompt in <work>/project, and then run `run.sh --verify <work>`.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../.." && pwd)"

usage() {
  sed -n '2,/^set -e/p' "${BASH_SOURCE[0]}" | sed '$d' | sed 's/^# \{0,1\}//'
  exit 2
}
if [[ "${1:-}" == --verify ]]; then
  [[ -f "${2:-}/meta.env" ]] || usage
  # shellcheck source=/dev/null
  source "$2/meta.env"
  "$here/verify/$skill.sh" "$2/project" "$fixture" "$style" "$version" | tee "$2/verify.txt"
  exit "${PIPESTATUS[0]}"
fi
[[ $# -ge 3 ]] || usage
agent=$1 skill=$2 fixture=$3
shift 3

style="google" published=false version="0.2.0" model="" keep=false prepare_only=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --style) style=$2; shift 2 ;;
    --published) published=true; shift ;;
    --sterling-version) version=$2; shift 2 ;;
    --model) model=$2; shift 2 ;;
    --keep) keep=true; shift ;;
    --prepare-only) prepare_only=true; keep=true; shift ;;
    *) usage ;;
  esac
done

[[ "$agent" == claude || $prepare_only == true ]] || { echo "run.sh: no headless runner for '$agent' yet; only claude." >&2; exit 2; }
if [[ -d "$here/fixtures/$fixture" ]]; then
  fixture_dir="$here/fixtures/$fixture"
elif [[ -d "$fixture" ]]; then
  fixture_dir="$(cd "$fixture" && pwd)"
else
  echo "run.sh: no fixture '$fixture'." >&2
  exit 2
fi
[[ -x "$here/verify/$skill.sh" ]] || { echo "run.sh: no verify script for '$skill'." >&2; exit 2; }
command -v claude >/dev/null || { echo "run.sh: claude is not on PATH." >&2; exit 2; }

work="$(mktemp -d "${TMPDIR:-/tmp}/sterling-eval.XXXXXX")"
echo "work directory: $work"
project="$work/project"
plugin="$repo/agent-plugins/sterling"
repo_url=""

if ! $published; then
  sim="$work/sim"
  mkdir -p "$sim" "$work/repo"
  rsync -a --exclude .git --exclude build --exclude .gradle --exclude .kotlin \
    --exclude 'agent-plugins/evals' "$repo/" "$sim/"
  echo "$version" >"$sim/version.txt"
  for manifest in plugin.json .claude-plugin/plugin.json .codex-plugin/plugin.json; do
    sed -i.bak -E "s/(\"version\": *\")[^\"]+/\1$version/" "$sim/agent-plugins/sterling/$manifest"
    rm "$sim/agent-plugins/sterling/$manifest.bak"
  done
  for skill_md in "$sim"/agent-plugins/sterling/skills/*/SKILL.md; do
    sed -i.bak -E "s/(sterling-version: *\")[^\"]+/\1$version/" "$skill_md"
    rm "$skill_md.bak"
  done
  cat >"$work/publish.init.gradle" <<GRADLE
allprojects {
  plugins.withId("maven-publish") {
    publishing { repositories { maven { name = "Eval"; url = uri("$work/repo") } } }
  }
}
GRADLE
  echo "publishing Sterling $version to a throwaway repository..."
  (cd "$sim" && ./gradlew -q --init-script "$work/publish.init.gradle" \
    :sterling-gradle-plugin:publishAllPublicationsToEvalRepository >"$work/publish.log" 2>&1) ||
    { cat "$work/publish.log" >&2; exit 1; }
  plugin="$sim/agent-plugins/sterling"
  repo_url="file://$work/repo"
fi

# --- The project: the fixture, the repo's wrapper, a git history of one commit.
mkdir -p "$project"
cp -R "$fixture_dir/." "$project/"
cp "$repo/gradlew" "$project/gradlew"
mkdir -p "$project/gradle/wrapper"
cp "$repo/gradle/wrapper/gradle-wrapper.jar" "$project/gradle/wrapper/"
[[ -f "$project/gradle/wrapper/gradle-wrapper.properties" ]] ||
  cp "$repo/gradle/wrapper/gradle-wrapper.properties" "$project/gradle/wrapper/"
chmod +x "$project/gradlew"
if [[ -n "$repo_url" ]]; then
  while IFS= read -r settings; do
    sed -i.bak "s|// @sterling-eval-repo|maven { url = uri(\"$repo_url\") }|" "$settings"
    rm "$settings.bak"
  done < <(find "$project" -name settings.gradle.kts)
fi
if [[ "$skill" == sterling-conventions ]]; then
  # The skill applies to a project that already has Sterling, here at STRICT.
  perl -pi -e "s|(id\\(\"io.spring.dependency-management\"\\) version \"1.1.7\")|\$1\n    id(\"dev.balakmran.sterling\") version \"$version\"|" "$project/build.gradle.kts"
  echo "sterling.profile=strict" >>"$project/gradle.properties"
fi
(
  cd "$project"
  git init -q -b main
  git config user.name "Eval" && git config user.email "eval@example.com"
  git add -A
  git commit -q -m "chore: initial project"
)

# --- The prompt pre-approves the plan, as the skills' guardrail 7 allows.
approval="I approve the plan in advance: go ahead with every step without asking me again. Don't push."
case "$skill" in
  adopt-sterling)
    prompt="Use the adopt-sterling skill to add Sterling to this project. Use format.style $(echo "$style" | tr '[:lower:]' '[:upper:]'). $approval"
    [[ "$fixture" == greenfield ]] && prompt="Use the adopt-sterling skill to add Sterling to this new project. $approval"
    ;;
  fix-sterling-findings)
    prompt="Use the fix-sterling-findings skill to make stlCheck pass. $approval"
    ;;
  upgrade-sterling)
    prompt="Use the upgrade-sterling skill to upgrade Sterling in this project. $approval"
    ;;
  sterling-conventions)
    prompt="Add a GET /greetings endpoint that returns a greeting for an optional 'name' query parameter (a service plus a controller, with a test), then run the checks. Keep to this project's conventions."
    ;;
  *) echo "run.sh: unknown skill '$skill'" >&2; exit 2 ;;
esac

printf 'skill=%s\nfixture=%s\nstyle=%s\nversion=%s\n' "$skill" "$fixture" "$style" "$version" >"$work/meta.env"
printf '%s\n' "$prompt" >"$work/prompt.txt"
ln -sfn "$plugin" "$work/plugin"
if $prepare_only; then
  echo "project: $project"
  echo "plugin:  $work/plugin"
  echo "prompt:  $work/prompt.txt"
  exit 0
fi

# --- Run the agent.
args=(-p "$prompt" --plugin-dir "$plugin" --setting-sources "project,local"
  --permission-mode acceptEdits --allowedTools "Bash" "Read" "Edit" "Write" "Glob" "Grep" "Skill"
  --output-format stream-json --verbose --no-session-persistence)
[[ -z "$model" ]] || args+=(--model "$model")
echo "running claude on $fixture with $skill..."
set +e
(cd "$project" && CLAUDE_CODE_DISABLE_AUTO_MEMORY=1 claude "${args[@]}" >"$work/transcript.jsonl" 2>"$work/agent.err")
agent_code=$?
set -e
echo "agent exit code: $agent_code (transcript: $work/transcript.jsonl)"

# --- Verify.
set +e
"$here/verify/$skill.sh" "$project" "$fixture" "$style" "$version" | tee "$work/verify.txt"
verify_code=${PIPESTATUS[0]}
set -e
if ((verify_code == 0)) && ! $keep; then
  # Gradle's caches live elsewhere; the work directory is only this run.
  find "$work" -mindepth 1 -delete 2>/dev/null || true
  rmdir "$work" 2>/dev/null || true
  echo "PASS"
else
  echo "work directory kept: $work"
fi
exit "$verify_code"
