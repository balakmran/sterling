---
name: adopt-sterling
description: >-
  Add Sterling (dev.balakmran.sterling), the Java 21+ Gradle quality gate
  (google-java-format, Error Prone, NullAway with JSpecify, SpotBugs, JaCoCo),
  to a new or existing Gradle project, as a short series of reviewable commits:
  plugin, one reformat commit hidden from git blame, the project's duplicate
  tool config removed, coverage thresholds set from the measured value, and a
  pre-commit hook. Use when asked to add, set up or adopt Sterling, or to add a
  quality gate, Error Prone or NullAway to a Gradle Java project. Stops with a
  clear message when Gradle is below 9.0 or the JDK is below 21.
license: Apache-2.0
allowed-tools: Bash(${CLAUDE_SKILL_DIR}/scripts/detect.sh) Bash(${CLAUDE_SKILL_DIR}/scripts/outcomes.sh:*) Bash(${CLAUDE_SKILL_DIR}/scripts/coverage.sh:*)
metadata:
  sterling-version: "0.2.0" # x-release-please-version
---

# Adopt Sterling

Sterling runs five tools behind one task, `stlCheck`, tuned by one profile.
This skill adds it to a Gradle project and leaves the result as commits a
person can review. It applies the Sterling version in this file's frontmatter
(`metadata.sterling-version`), never "latest".

Scripts are in this skill's `scripts/` directory. Run them by that path (Claude
Code: `${CLAUDE_SKILL_DIR}/scripts/detect.sh`; other agents: the skill's
directory plus `scripts/detect.sh`), from the project root. Reference pages are
in `reference/`.

## Guardrails

These hold for the whole run.

1. Stop on a dirty working tree. If the current branch is the default branch,
   create `sterling/adopt-sterling` first. Never push, never open a PR.
2. Change build files, `gradle.properties`, `.git-blame-ignore-revs`, hook
   config and what `stlFmt` rewrites, and nothing else. Never change business
   logic or tests. If a step needs that, stop and point to the
   `fix-sterling-findings` skill.
3. Never lower a threshold below the measured value, never switch a check or
   tool off, and never add a suppression or exclude without saying which,
   where and why in the report.
4. Decide from task outcomes (`scripts/outcomes.sh`), `stlInfo --json` and
   report files, never from Gradle's log text.
5. Never upgrade Gradle, the wrapper, the toolchain or the daemon JVM. Never
   write an agent's project or workspace plugin settings.
6. Never use `--no-verify`, `-x <task>` or `--offline` to get something to pass.
7. Show the plan and wait for confirmation before the first change, unless the
   request already approved it. Create the branch (guardrail 1) once the plan is
   confirmed.
8. End with the report (below).

## 1. Detect

Run `scripts/detect.sh`. It prints one JSON document and exits 3 when the
project is unsupported: no wrapper, Gradle below 9.0, the Gradle daemon on a
JDK below 21, or a Java toolchain below 21. Then `supported` is false and
`stopReason` says what to upgrade (the `projects` list is then absent). **Stop
there**, even when the request pre-approved every step: tell the user, change
nothing, create no branch and make no commit. Never offer to do the upgrade.

Otherwise read: `git` (clean? default branch? `hookManager`,
`blameIgnoreRevs`), `versionCatalog`, `buildLogic`, `sterlingVersion` (already
applied?), and per project in `projects`: `plugins`, `toolchain`,
`dependencies` (Lombok, MapStruct, Error Prone, NullAway, JSpecify),
`generatedSourceDirs`, `javaSources`, `nullMarkedPackages`,
`fourSpaceIndentShare`, `jacocoRules`. If `git.clean` is false, stop and ask
the user to commit or stash. If Sterling is already applied, stop and point
to `upgrade-sterling`. Also read the build scripts for what detection can't
see: Spotless steps, SpotBugs settings, `options.errorprone` options and
JaCoCo excludes.

## 2. Classify and plan

**New** when no Java project has more than 5 main sources or the user says so;
**existing** otherwise. Build the plan from `reference/plan-template.md`,
show it, and wait for confirmation (guardrail 7). It names:

- where the plugin goes: the version catalog and `plugins { }`, or the
  `build-logic`/`buildSrc` convention plugin when the build has one (see
  `reference/multi-module.md`)
- `format.style`: `GOOGLE` unless the user picks `AOSP`. When
  `fourSpaceIndentShare` is 50 or more in the Java projects, say that `AOSP`
  (4-space indentation, same formatter) would make the reformat far smaller,
  and let the user choose when you can ask. When you can't, or the request
  already named a style, use that style and say in the report that `AOSP`
  would have shrunk the reformat
- the reformat commit, or `format.ratchetFrom` instead, which formats only
  changed files and skips the reformat and blame commits (needs the base
  branch fetched in CI)
- the duplicate config to remove (`reference/duplicate-config.md`)
- the hook and where it goes (`reference/hooks.md`)
- what detection couldn't tell

## 3a. New project

1. Add the plugin as in 3b commit 1 (last in `plugins { }` when there is no
   catalog or convention plugin) and `stlFmt` the sources; commit both as
   `build: add Sterling <version>`. No `sterling.profile`: the default is
   `STANDARD`. There is no separate reformat or blame commit.
2. `scripts/outcomes.sh stlCheck`. If it fails, report the failing tasks and
   point to `fix-sterling-findings`; don't lower anything.
3. Add the hook (`reference/hooks.md`) and commit it as
   `chore: check formatting before every commit`.

## 3b. Existing project

Commit in this order, one commit each, with Conventional Commit subjects.
After each, `git status` is clean.

1. **`build: add Sterling <version> at LENIENT`.** Add the plugin (version
   catalog entry and `alias(...)`, or `id("dev.balakmran.sterling") version
   "<version>"`; Groovy builds use `id 'dev.balakmran.sterling' version
   '<version>'`) and `sterling.profile=lenient` in `gradle.properties`. Sterling
   refuses to configure beside the `com.github.spotbugs` plugin or Spotless's
   `googleJavaFormat()`/`formatAnnotations()` steps, so remove exactly those in
   this commit and name them in its body; the rest of the duplicates go in
   commit 4. A `spotbugs { }` block and its `com.github.spotbugs.snom` imports
   can't compile without that plugin, so move those settings to
   `sterling { spotbugs { } }` in this commit (see `reference/settings.md` for
   the types), put their values in the commit body, and drop the SpotBugs
   plugin dependency from `build-logic` with them. Apply Sterling after any plugin it replaces. Then
   `scripts/outcomes.sh compileJava compileTestJava` must succeed; Error
   Prone's error-level findings fail compilation in every profile. If it
   fails, stop and hand over to `fix-sterling-findings` (with the user's
   go-ahead).
2. **`style: reformat with google-java-format <version>`** (skip this and
   commit 3 when `stlFmt` changes nothing: the sources were already formatted,
   and the report says so and leaves out the `blame.ignoreRevsFile` step) (the version is
   the formatter's, from `stlInfo --json`; skip when using
   `format.ratchetFrom`). Set `format.style` first when it isn't `GOOGLE`, in
   commit 1. Run `scripts/outcomes.sh stlFmt`, commit only that: stage with
   `git add -A` and check `git diff --cached --name-only` lists nothing but
   `.java` files. Then verify it's formatting only: run `stlFmt` again and
   `git status --porcelain` must be empty, and `scripts/outcomes.sh
   stlFmtCheck` must succeed. In task outcomes, `UP_TO_DATE` with exit code 0
   counts as success.
3. **`chore: ignore the reformat in git blame`.** Append the reformat commit's
   SHA, with a comment, to `.git-blame-ignore-revs` (create it if missing).
   Tell the user in the report to run `git config blame.ignoreRevsFile
   .git-blame-ignore-revs`; don't change git config.
4. **`build: let Sterling own the tool configuration`.** Remove what
   `reference/duplicate-config.md` lists. Move each setting to its Sterling
   equivalent in the same commit. After this,
   `scripts/outcomes.sh stlInfo stlFmtCheck compileJava` must succeed and
   `scripts/outcomes.sh -q stlInfo --json` must show what you moved. If
   compilation now fails because the project's own `disableAllChecks` is gone,
   that is Error Prone finding real problems: stop and hand over to
   `fix-sterling-findings`.
5. **`build: set coverage thresholds from the measured value`.** Run
   `scripts/outcomes.sh test jacocoTestReport -Psterling.profile=standard`,
   which runs every `Test` task with the agent on. For each module, take
   `coverage.reports.xml` from `scripts/outcomes.sh -q stlInfo --json` (one
   line per module, `project` names it). The path is relative to that module's
   directory, so prefix the module's directory, and run
   `scripts/coverage.sh <that file>` from the project root. Put the rounded-down values in that
   module's own build script: `sterling { coverage { instruction = N; branch = M } }`,
   even when a convention plugin applies Sterling. A module with `branches` of 0
   gets `branch = 0`; say so in the report. Never set a value above the
   measurement. A minimum the project set before that was higher than the
   measurement was not being enforced: report it, don't keep it.
6. **`build: move to STANDARD`**, only if `scripts/outcomes.sh stlCheck
   -Psterling.profile=standard` succeeds: remove `sterling.profile` from
   `gradle.properties` (delete the file if it's empty and new). Otherwise stay
   on `LENIENT` and list in the report each failing task, with its findings'
   count, and point to `fix-sterling-findings`.
7. **`chore: check formatting before every commit`.** The hook, from
   `reference/hooks.md`, committed as config so the team gets it. Never run
   `prek install` or touch `.git/hooks`: the report asks the user to.

## 4. Report

Finish with:

- what you ran (the scripts and tasks) and what each returned
- the commits: SHA and subject
- the final `scripts/outcomes.sh -q stlInfo --json`, summarised: profile,
  format style, thresholds per module
- everything left off, kept, suppressed or excluded, with why: Checkstyle and
  PMD (left alone), a project tool version newer than Sterling's (kept), a
  `branch = 0` module, a profile left on `LENIENT`
- what the user does next: `git config blame.ignoreRevsFile
  .git-blame-ignore-revs`; `prek install` (or the project's hook manager) so
  the hook runs; mark packages `@NullMarked` one at a time; run `STRICT` in CI
  with `-Psterling.profile=strict`; and, if the profile is `LENIENT`, run the
  `fix-sterling-findings` skill

## Sterling settings this skill uses

Only these exist, so only these appear in a plan: `format.style`,
`format.ratchetFrom`, `errorProne.disable(...)`, `nullAway.jspecifyMode`,
`spotbugs.effort`, `spotbugs.reportLevel`, `spotbugs.excludeFilter`,
`coverage.instruction`, `coverage.branch`, `coverage.testTasks`,
`coverage.excludes`. `reference/settings.md` says when each applies.
