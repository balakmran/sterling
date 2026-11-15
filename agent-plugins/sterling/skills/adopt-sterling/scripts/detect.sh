#!/usr/bin/env bash
# Describes a Gradle project for adopt-sterling, as one JSON document on standard output.
#
# Two phases. The static phase reads files only: the Gradle and daemon JVM versions, git state,
# hook managers, and where Sterling would go. It stops (exit 3, "supported": false) when there's no
# wrapper, Gradle is below 9.0, or the daemon JVM is below 21, without running Gradle. The Gradle
# phase then runs a read-only init script for each project's plugins, toolchain, dependencies and
# sources, and stops (exit 3) when a Java project's toolchain is below 21.
#
# Usage: detect.sh (run from the project root)
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

json_string() {
  local s=${1//\\/\\\\}
  s=${s//\"/\\\"}
  printf '"%s"' "$s"
}

stop() {
  printf '{"supported":false,"stopReason":%s,%s}\n' "$(json_string "$1")" "$static_json"
  exit 3
}

major() { sed -E 's/^([0-9]+).*/\1/' <<<"$1"; }

# --- Static phase ---

wrapper_props="gradle/wrapper/gradle-wrapper.properties"
gradle_version=""
if [[ -f "$wrapper_props" ]]; then
  gradle_version="$(sed -nE 's/^distributionUrl=.*gradle-([0-9][0-9.]*[0-9])(-[a-z0-9-]+)?-(bin|all)\.zip$/\1/p' "$wrapper_props")"
fi

daemon_jvm=""
daemon_source=""
if [[ -f gradle/gradle-daemon-jvm.properties ]]; then
  daemon_jvm="$(sed -nE 's/^toolchainVersion=([0-9]+).*/\1/p' gradle/gradle-daemon-jvm.properties)"
  daemon_source="gradle-daemon-jvm.properties"
fi
if [[ -z "$daemon_jvm" ]]; then
  java_bin="java"
  [[ -n "${JAVA_HOME:-}" ]] && java_bin="$JAVA_HOME/bin/java"
  version_line="$("$java_bin" -version 2>&1 | head -1 || true)"
  daemon_jvm="$(sed -nE 's/.*version "([0-9]+).*/\1/p' <<<"$version_line")"
  daemon_source="JAVA_HOME"
fi

git_repo=false clean=false branch="" default_branch="" hook_manager="none"
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git_repo=true
  [[ -z "$(git status --porcelain)" ]] && clean=true
  branch="$(git branch --show-current)"
  default_branch="$(git symbolic-ref --quiet --short refs/remotes/origin/HEAD 2>/dev/null | sed 's|^origin/||' || true)"
  if [[ -z "$default_branch" ]]; then
    for candidate in main master; do
      if git show-ref --verify --quiet "refs/heads/$candidate"; then
        default_branch=$candidate
        break
      fi
    done
  fi
  if [[ -f .pre-commit-config.yaml ]]; then
    hook_manager="pre-commit"
  elif [[ -d .husky ]]; then
    hook_manager="husky"
  elif [[ -f lefthook.yml || -f .lefthook.yml || -f lefthook.yaml ]]; then
    hook_manager="lefthook"
  elif [[ -n "$(git config --get core.hooksPath || true)" ]]; then
    hook_manager="core.hooksPath"
  elif [[ -f "$(git rev-parse --git-path hooks)/pre-commit" ]]; then
    hook_manager="git-hook"
  fi
fi

build_logic="none"
[[ -d buildSrc ]] && build_logic="buildSrc"
[[ -d build-logic ]] && build_logic="build-logic"

sterling_version="$(grep -rhoE --include='*.gradle.kts' --include='*.gradle' --include='libs.versions.toml' \
  '(dev\.balakmran\.sterling[^0-9]{1,40}|sterling[-_a-z]* *= *")[0-9]+\.[0-9]+\.[0-9]+' . 2>/dev/null |
  grep -oE '[0-9]+\.[0-9]+\.[0-9]+$' | head -1 || true)"

static_json="$(printf '"gradle":{"version":%s,"wrapper":%s},"daemonJvm":{"version":%s,"source":%s},"git":{"repository":%s,"clean":%s,"branch":%s,"defaultBranch":%s,"hookManager":%s,"blameIgnoreRevs":%s},"versionCatalog":%s,"buildLogic":%s,"sterlingVersion":%s' \
  "$(json_string "$gradle_version")" "$([[ -x ./gradlew ]] && echo true || echo false)" \
  "${daemon_jvm:-null}" "$(json_string "$daemon_source")" \
  "$git_repo" "$clean" "$(json_string "$branch")" "$(json_string "$default_branch")" \
  "$(json_string "$hook_manager")" "$([[ -f .git-blame-ignore-revs ]] && echo true || echo false)" \
  "$([[ -f gradle/libs.versions.toml ]] && echo true || echo false)" "$(json_string "$build_logic")" \
  "$(json_string "$sterling_version")")"

[[ -x ./gradlew && -f "$wrapper_props" ]] || stop "No Gradle wrapper. Sterling needs the build to use ./gradlew."
[[ -n "$gradle_version" ]] || stop "Couldn't read the Gradle version from $wrapper_props."
(($(major "$gradle_version") >= 9)) || stop "Gradle $gradle_version: Sterling needs Gradle 9.0 or later. Upgrade the wrapper first."
[[ -n "$daemon_jvm" ]] || stop "Couldn't find the JDK Gradle runs on."
((daemon_jvm >= 21)) || stop "Gradle runs on JDK $daemon_jvm ($daemon_source): Sterling needs JDK 21 or later for the Gradle daemon."

# --- Gradle phase ---

set +e
gradle_output="$(./gradlew -q --no-configuration-cache --init-script "$script_dir/detect.init.gradle.kts" sterlingDetect 2>&1)"
gradle_code=$?
set -e
if ((gradle_code != 0)); then
  stop "The build failed while detecting: $(tail -20 <<<"$gradle_output")"
fi
projects="$(grep '^STERLING_DETECT ' <<<"$gradle_output" | sed 's/^STERLING_DETECT //' | paste -sd, -)"

# A Java project without a toolchain compiles with the daemon's JDK.
below=""
while read -r line; do
  [[ "$line" == *'"java"'* ]] || continue
  path="$(sed -nE 's/^\{"path":"([^"]*)".*/\1/p' <<<"$line")"
  toolchain="$(sed -nE 's/.*"toolchain":([0-9]+|null).*/\1/p' <<<"$line")"
  [[ "$toolchain" == "null" || -z "$toolchain" ]] && toolchain=$daemon_jvm
  ((toolchain >= 21)) || below="$below $path ($toolchain)"
done < <(grep '^STERLING_DETECT ' <<<"$gradle_output" | sed 's/^STERLING_DETECT //')

static_json="$static_json,\"projects\":[${projects}]"
[[ -z "$below" ]] || stop "Java toolchain below 21 in:$below. Sterling needs Java 21 or later."

printf '{"supported":true,"stopReason":null,%s}\n' "$static_json"
