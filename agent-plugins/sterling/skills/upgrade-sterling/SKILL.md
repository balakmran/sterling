---
name: upgrade-sterling
description: >-
  Upgrade Sterling (dev.balakmran.sterling) in a Gradle project to the version
  this skill was released with, following the upgrade notes: the version bump
  and any setting changes in one commit, a separate reformat commit only when
  google-java-format changed, then a gate run that reports new findings. Use
  when asked to upgrade or update Sterling, or when the project's Sterling is
  older than this skill's.
license: Apache-2.0
allowed-tools: Bash(${CLAUDE_SKILL_DIR}/scripts/outcomes.sh:*) Bash(${CLAUDE_SKILL_DIR}/scripts/coverage.sh:*)
metadata:
  sterling-version: "0.2.0" # x-release-please-version
---

# Upgrade Sterling

Moves a project to the Sterling version in this file's frontmatter
(`metadata.sterling-version`), never to "latest". Scripts are in this skill's
`scripts/` directory; run them by that path (Claude Code:
`${CLAUDE_SKILL_DIR}/scripts/outcomes.sh`) from the project root.

## Guardrails

1. Stop on a dirty working tree. If the current branch is the default branch,
   create `sterling/upgrade-sterling` first. Never push, never open a PR.
2. Change only the Sterling version, the settings the upgrade notes name, and
   what `stlFmt` rewrites. Never change business logic or tests, and never fix
   the findings an upgrade brings: report them and point to the
   `fix-sterling-findings` skill.
3. Never lower a threshold, disable a check, move the profile down, or add a
   suppression or exclude to make the gate pass.
4. Never upgrade Gradle, the wrapper, the toolchain or the daemon JVM. Never
   write an agent's project or workspace plugin settings.
5. Decide from task outcomes and `stlInfo --json`, not from Gradle's log text.
   Never use `--no-verify`, `-x <task>` or `--offline`.
6. Show the plan and wait for confirmation before the first change, unless the
   request already approved it.

## 1. Read the current version

Run `scripts/outcomes.sh -q stlInfo --json` and read `sterling` (the version
in use), the formatter's `google-java-format` version, and each tool's `version` and
`versionSource`. A 0.1.0 project has no `--json`: the run fails (`outcomes.sh` still exits 0
and records `exitCode` 1), which is the signal to read the version in
`gradle/libs.versions.toml` or the `plugins { }` block of the build scripts or
convention plugin, and read the text `scripts/outcomes.sh stlInfo` prints for
the formatter's version.

Stop, changing nothing, when:

- Sterling isn't applied: point to the `adopt-sterling` skill
- the project is already at this skill's version, or newer: say so, and that a
  newer one needs an update of the agent plugin
- the project is at a version more than one minor release behind: upgrade one
  minor at a time, and say so (this skill covers the notes for its own release)

## 2. Read the upgrade notes and plan

Fetch the notes for the target version from
`https://raw.githubusercontent.com/balakmran/sterling/v<version>/docs/guide/upgrading.md`
(the section from the project's version to this one). Don't use the
changelog: its entries are commit subjects. If the page can't be fetched, stop
and say so.

Show a plan: the version bump, each setting the notes change, each exclude or
option they make redundant, whether google-java-format's version changes, and
what may add findings. Wait for confirmation (guardrail 6).

## 3. Commits

1. **`build: upgrade Sterling to <version>`**: the version in the catalog or
   `plugins { }` block (or the `build-logic` dependency), and every change the
   notes call for in the same commit. For 0.1.0 to 0.2.0 that is:
   - remove `NullAway:CustomContractAnnotations`,
     `NullAway:CheckContracts` and `NullAway:HandleTestAssertionLibraries`
     options from the project's `options.errorprone` settings
   - remove a `coverage.excludes` entry that only covers the Spring Boot
     application class (for example `com/example/Application*`) when the
     project applies `org.springframework.boot`, or classes from generated
     sources
   - remove a tool version override (Error Prone, NullAway, JSpecify, SpotBugs,
     JaCoCo) that the new Sterling's version meets or exceeds; a newer
     override stays, reported
   - keep `format.style` and `format.ratchetFrom` as they are

   Remove what a removal leaves empty: a `tasks.withType<JavaCompile>` or
   `sterling { }` block with nothing in it, and an import nothing uses.
2. If google-java-format's version changed (compare the `format` versions before
   and after with `stlInfo --json`): run `scripts/outcomes.sh stlFmt` and
   commit it alone as `style: reformat with google-java-format <version>`,
   then append its SHA, with a comment, to `.git-blame-ignore-revs` in a third
   commit, `chore: ignore the reformat in git blame`. When it didn't change,
   make no reformat commit.
3. If coverage's counted tasks or excluded classes changed (the new version
   counts every `Test` task and leaves out generated classes and the Spring
   Boot main class; the `coverage` block of `stlInfo --json` shows what
   applies),
   re-measure: `scripts/outcomes.sh test jacocoTestReport`, then for each
   module run `scripts/coverage.sh <coverage.reports.xml from stlInfo --json>`.
   Report the new numbers against the thresholds, and say when they are now
   well above them: raising a threshold is the user's call, so leave it. Never
   lower one, even when the measurement is below it.

## 4. Run the gate and report

Run `scripts/outcomes.sh stlCheck --continue`. New findings from tool bumps are
reported with their tasks, not fixed, and the profile is not lowered. The
report lists:

- the version change, and the notes' section it followed
- the commits (SHA and subject)
- every setting or exclude removed, and every override kept, with why
- whether google-java-format changed, and so whether there is a reformat commit
- the outcome of `stlCheck`, from the task outcomes, and a pointer to
  `fix-sterling-findings` when it failed
- the new coverage numbers when they changed
