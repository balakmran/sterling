#!/usr/bin/env bash
# Prints a module's measured coverage from a JaCoCo XML report, each rounded down to a whole
# percent, as JSON: {"instruction": N, "branch": N, "branches": total}.
# "branches" is 0 when the code has no branches; branch is then reported as 0.
#
# Usage: coverage.sh <path to jacocoTestReport.xml>
set -euo pipefail

if [[ $# -ne 1 || ! -f "$1" ]]; then
  echo "usage: coverage.sh <jacocoTestReport.xml>" >&2
  exit 2
fi

# The report's own totals are the counters after the last </package>.
tail_counters="$(awk 'BEGIN { RS = ">" } /<\/package/ { buf = ""; next } { buf = buf $0 ">\n" } END { printf "%s", buf }' "$1")"

counter() {
  local line missed covered
  line="$(grep -o "<counter type=\"$1\" missed=\"[0-9]*\" covered=\"[0-9]*\"" <<<"$tail_counters" | tail -1 || true)"
  if [[ -z "$line" ]]; then
    echo "0 0"
    return
  fi
  missed="$(sed -E 's/.*missed="([0-9]+)".*/\1/' <<<"$line")"
  covered="$(sed -E 's/.*covered="([0-9]+)".*/\1/' <<<"$line")"
  echo "$missed $covered"
}

floor_percent() {
  local missed=$1 covered=$2 total=$(($1 + $2))
  if ((total == 0)); then
    echo 0
  else
    echo $((covered * 100 / total))
  fi
}

read -r i_missed i_covered < <(counter INSTRUCTION)
read -r b_missed b_covered < <(counter BRANCH)
printf '{"instruction":%d,"branch":%d,"branches":%d}\n' \
  "$(floor_percent "$i_missed" "$i_covered")" \
  "$(floor_percent "$b_missed" "$b_covered")" \
  "$((b_missed + b_covered))"
