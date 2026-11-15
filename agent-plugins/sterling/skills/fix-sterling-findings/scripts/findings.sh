#!/usr/bin/env bash
# Collects what Sterling's gate found into one list, as JSON lines on standard output.
#
# Reads, from the project root and every module below it:
#   build/sterling-agent/gradle.log  javac's diagnostics, written by outcomes.sh: Error Prone and
#                                    NullAway findings ([CheckName]) and javac's own warnings
#   build/reports/spotbugs/main.xml  SpotBugs
#   build/reports/jacoco/test/jacocoTestReport.xml
#                                    JaCoCo: the module's totals and its least-covered classes
#
# Each line is an object with a "tool" (ErrorProne, NullAway, javac, SpotBugs, JaCoCo) and:
#   compiler and SpotBugs findings: "check", "severity", "file", "line", "message"
#   JaCoCo: {"tool":"JaCoCo","kind":"total","module":..,"instruction":N,"branch":N} once per module,
#           and {"tool":"JaCoCo","kind":"class","module":..,"class":..,"missed":N,"total":N} for the
#           ten classes with the most missed instructions
# Paths are relative to the project root. Formatting has no list: the fix is stlFmt.
#
# Usage: findings.sh (run from the project root, after outcomes.sh)
set -euo pipefail

if [[ $# -ne 0 ]]; then
  echo "usage: findings.sh (from the project root)" >&2
  exit 2
fi

root="$PWD"

# --- javac diagnostics: /abs/Path.java:12: error: [Check] message
log="build/sterling-agent/gradle.log"
if [[ -f "$log" ]]; then
  awk -v root="$root/" '
    function esc(s) { gsub(/\\/, "\\\\", s); gsub(/"/, "\\\"", s); gsub(/\t/, " ", s); return s }
    { line = $0; sub(/^[ \t]+/, "", line) }
    match(line, /^[^ ].*\.java:[0-9]+: (error|warning): \[[A-Za-z0-9_.]+\] /) {
      file = line; sub(/:[0-9]+: (error|warning): .*/, "", file)
      sub("^" root, "", file)
      num = line; sub(/^.*\.java:/, "", num); sub(/:.*/, "", num)
      severity = (line ~ /: error: /) ? "error" : "warning"
      check = line; sub(/^.*: (error|warning): \[/, "", check); sub(/\].*/, "", check)
      message = line; sub(/^.*: (error|warning): \[[A-Za-z0-9_.]+\] /, "", message)
      tool = (check == "NullAway") ? "NullAway" : (check ~ /^(removal|deprecation|unchecked|rawtypes|serial|cast|static|fallthrough|dep-ann|overrides|varargs|try|text-blocks|this-escape|preview|lossy-conversions|synchronization|exports|opens|requires-automatic|requires-transitive-automatic|module|options|processing|path|classfile|auxiliaryclass|divzero|empty|finally|missing-explicit-ctor|output-file-clash|strictfp|serial)$/) ? "javac" : "ErrorProne"
      printf "{\"tool\":\"%s\",\"check\":\"%s\",\"severity\":\"%s\",\"file\":\"%s\",\"line\":%s,\"message\":\"%s\"}\n", tool, esc(check), severity, esc(file), num, esc(message)
    }' "$log" | sort -u
fi

# --- SpotBugs and JaCoCo, per module
module_dirs() {
  find . -type d -name build -prune -print 2>/dev/null |
    grep -v '/build/' | grep -v '/\.gradle/' | sed 's|/build$||' | sort
}

while IFS= read -r dir; do
  module="${dir#./}"
  [[ "$module" == "." || -z "$module" ]] && module=":" || module=":${module//\//:}"
  base="$dir"

  spotbugs="$base/build/reports/spotbugs/main.xml"
  if [[ -f "$spotbugs" ]]; then
    awk -v base="$([[ "$dir" == . ]] && echo "" || echo "${dir#./}")" '
      function esc(s) { gsub(/\\/, "\\\\", s); gsub(/"/, "\\\"", s); gsub(/[\t\n]/, " ", s); return s }
      function attr(tag, name,   m) {
        if (match(tag, name "=\"[^\"]*\"")) { m = substr(tag, RSTART + length(name) + 2, RLENGTH - length(name) - 3); return m }
        return ""
      }
      BEGIN { RS = ">" }
      /<BugInstance / { inBug = 1; type = attr($0, "type"); priority = attr($0, "priority"); path = ""; start = ""; message = ""; primaryFound = 0; next }
      inBug && /<LongMessage/ { inMsg = 1; next }
      inBug && inMsg { message = $0; sub(/<\/LongMessage$/, "", message); sub(/<.*/, "", message); inMsg = 0; next }
      # The bug location is the SourceLine marked primary; the enclosing method range comes before it.
      inBug && /<SourceLine / {
        s = attr($0, "start")
        if (s != "" && ($0 ~ /primary="true"/ || start == "" || !primaryFound)) {
          start = s; path = attr($0, "sourcepath")
          if ($0 ~ /primary="true"/) primaryFound = 1
        }
        next
      }
      /<\/BugInstance/ {
        if (inBug) {
          file = (path == "") ? "" : ((base == "" ? "" : base "/") "src/main/java/" path)
          sev = (priority == "1") ? "high" : (priority == "2") ? "medium" : "low"
          printf "{\"tool\":\"SpotBugs\",\"check\":\"%s\",\"severity\":\"%s\",\"file\":\"%s\",\"line\":%s,\"message\":\"%s\"}\n", esc(type), sev, esc(file), (start == "" ? "null" : start), esc(message)
        }
        inBug = 0; inMsg = 0
      }' "$spotbugs"
  fi

  jacoco="$base/build/reports/jacoco/test/jacocoTestReport.xml"
  if [[ -f "$jacoco" ]]; then
    awk -v module="$module" '
      function esc(s) { gsub(/\\/, "\\\\", s); gsub(/"/, "\\\"", s); return s }
      function attr(tag, name) {
        if (match(tag, name "=\"[^\"]*\"")) return substr(tag, RSTART + length(name) + 2, RLENGTH - length(name) - 3)
        return ""
      }
      BEGIN { RS = ">" }
      /<class / { cls = attr($0, "name"); inMethod = 0; next }
      /<method / { inMethod = 1; next }
      /<\/method/ { inMethod = 0; next }
      /<\/class/ { cls = ""; next }
      /<\/package/ { inPackage = 0; next }
      /<package / { inPackage = 1; next }
      /<counter / {
        type = attr($0, "type"); missed = attr($0, "missed") + 0; covered = attr($0, "covered") + 0
        if (cls != "" && !inMethod && type == "INSTRUCTION") {
          classMissed[cls] = missed; classTotal[cls] = missed + covered
        } else if (cls == "" && !inPackage) {
          # After the last package: the report totals.
          if (type == "INSTRUCTION") { im = missed; ic = covered }
          if (type == "BRANCH") { bm = missed; bc = covered }
        }
        next
      }
      END {
        ip = (im + ic > 0) ? int(ic * 100 / (im + ic)) : 0
        bp = (bm + bc > 0) ? int(bc * 100 / (bm + bc)) : 0
        printf "{\"tool\":\"JaCoCo\",\"kind\":\"total\",\"module\":\"%s\",\"instruction\":%d,\"branch\":%d,\"branches\":%d}\n", module, ip, bp, bm + bc
        n = 0
        for (c in classMissed) if (classMissed[c] > 0) order[++n] = c
        for (i = 2; i <= n; i++) {
          key = order[i]; j = i - 1
          while (j >= 1 && classMissed[order[j]] < classMissed[key]) { order[j + 1] = order[j]; j-- }
          order[j + 1] = key
        }
        for (i = 1; i <= n && i <= 10; i++) {
          printf "{\"tool\":\"JaCoCo\",\"kind\":\"class\",\"module\":\"%s\",\"class\":\"%s\",\"missed\":%d,\"total\":%d}\n", module, esc(order[i]), classMissed[order[i]], classTotal[order[i]]
        }
      }' "$jacoco"
  fi
done < <(module_dirs)
