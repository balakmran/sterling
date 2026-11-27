---
name: fix-sterling-findings
description: >-
  Fix what Sterling's quality gate (dev.balakmran.sterling) reports: Error
  Prone and NullAway compile errors, SpotBugs findings, coverage below the
  threshold and formatting, in rounds, with one commit per tool. Use when
  stlCheck, stlLint, stlSec, stlCov or stlFmtCheck failed, or when asked to fix
  the Sterling, Error Prone, NullAway, SpotBugs or coverage findings. Applies
  Error Prone's automatic fixes first, then fixes code and writes tests;
  suppresses only as a last resort, with a reason.
license: Apache-2.0
allowed-tools: Bash(${CLAUDE_SKILL_DIR}/scripts/outcomes.sh:*) Bash(${CLAUDE_SKILL_DIR}/scripts/findings.sh)
metadata:
  sterling-version: "0.2.0" # x-release-please-version
---

# Fix Sterling's findings

Sterling's gate is `stlCheck`: formatting, Error Prone and NullAway,
SpotBugs and coverage. This skill makes it pass by fixing the code, not the
gate. Per-tool guidance is in `reference/`.

Scripts are in this skill's `scripts/` directory; run them by that path (Claude
Code: `${CLAUDE_SKILL_DIR}/scripts/outcomes.sh`) from the project root.

## Guardrails

1. Stop on a dirty working tree. If the current branch is the default branch,
   create `sterling/fix-findings` first. Never push, never open a PR.
2. This skill may change business code and tests, and nothing else: no
   `sterling { }` settings, no `gradle.properties`, no build files, except a
   SpotBugs exclude filter the project already has, for a last-resort
   suppression.
3. Never lower a coverage threshold, add to `coverage.excludes`, call
   `errorProne { disable(...) }`, switch a tool off or move the profile down.
   Never mark a package `@NullMarked` or remove it to make findings go away,
   and never add a `@Generated` annotation to dodge coverage.
4. Never change an existing test to make it pass; a failing existing test is
   a finding to report.
5. Decide from task outcomes and the findings list, not from Gradle's log
   text. Never use `--no-verify`, `-x <task>` or `--offline`.
6. A change that alters behaviour on purpose (a null check that now throws) is
   reported, with the reason.
7. Stop after 5 rounds and report what's left.

## 1. Find what fails

Run `scripts/outcomes.sh stlCheck --continue` and read
`build/sterling-agent/outcomes.json`: which tasks are `FAILED`. Then
`scripts/findings.sh` prints every finding as one JSON line (from javac's
diagnostics and the tools' reports; the console lines Sterling prints for
SpotBugs and JaCoCo are `<path>[:<line>] [<Tool>:<Rule>] <message>`, console
format 1, and name the real source path): compile findings
(`ErrorProne`, `NullAway`, `javac`), `SpotBugs` findings, and `JaCoCo`
totals with the classes missing the most coverage. Read the thresholds from
`scripts/outcomes.sh -q stlInfo --json` (the lines that start with `{`;
the script adds one `outcomes:` line after them) as `coverage.instruction`,
`coverage.branch` and `profile`. The tasks in `outcomes.json` are listed
alphabetically, not in execution order.

Gradle skips what depends on a failed compile, so the first run shows compile
findings only. Each round runs the gate again and finds the next layer.

## 2. Fix, in this order

One commit per tool (compile findings from Error Prone and NullAway share one),
formatting last so its diff is the formatter's. Conventional
Commit subjects: `fix:` for code, `test:` for new tests, `style:` for `stlFmt`.

0. **Automatic fixes** (once, in round 1, before the numbered steps): run
   `scripts/outcomes.sh stlFix` on its own. It changes sources, so it never
   shares a Gradle run with `stlCheck` or another verification task, and it
   also formats what it touches. When the project's Sterling has no `stlFix`
   task (older than 0.3.0), skip it. If it changed files, commit them alone as
   `fix: apply Error Prone's fixes with stlFix`, after checking
   `git diff --cached --name-only` lists only `.java` files, then run
   `scripts/outcomes.sh stlCheck --continue` again: what's left is yours to
   fix. It patches only `errorProne.patchChecks`, so most findings remain.
1. **Compile findings** (`ErrorProne`, `NullAway`, `javac`): `reference/error-prone.md`
   and `reference/nullaway.md`. Re-run `scripts/outcomes.sh stlLint` until
   `compileJava` and `compileTestJava` succeed.
2. **SpotBugs** (`stlSec`): `reference/spotbugs.md`.
3. **Coverage** (`stlCov`): `reference/coverage.md`. Write tests for the
   classes `findings.sh` lists.
4. **Formatting** (`stlFmtCheck`): `scripts/outcomes.sh stlFmt`, commit alone;
   no commit when it changed nothing.

Each fix addresses the cause the finding names. Prefer a fix that keeps public
signatures; when only a signature change works, make it and list it in the
report. Add tests as new test classes beside the existing ones; don't edit
existing test methods. A finding that is wrong is
suppressed, last, as below.

## 3. Suppress as a last resort

Only for a finding that is wrong, never for one that is inconvenient.

- Error Prone and NullAway: `@SuppressWarnings("CheckName")` on the smallest
  scope, with a comment on the line above giving the reason.
- SpotBugs: a `<Match>` in the project's existing `spotbugs.excludeFilter`
  file, with an XML comment giving the reason. If the project has none,
  report the finding instead; creating the filter is a build change.

Every suppression is listed in the report: file, check, reason.

## 4. Repeat, then report

Run `scripts/outcomes.sh stlCheck --continue` again after each round, until
`stlCheck` succeeds or round 5 ends. The report lists:

- the rounds: what failed, what was fixed, the commits (SHA and subject)
- the final outcome of `stlCheck`, from the task outcomes
- every suppression with its reason, and every behaviour change
- what is left, with the findings' counts, and why it was left
- any existing test that fails
