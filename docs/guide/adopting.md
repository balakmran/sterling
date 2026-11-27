# Adopting Sterling

## A path for an existing codebase

Each step is one pull request, and `main` stays green throughout:

1. **Apply Sterling on `LENIENT`.** `sterling.profile=lenient` in
   `gradle.properties`. The tools run on demand, but `check` doesn't run the
   gate, and Error Prone's and NullAway's errors are reported as warnings, so
   nothing new fails `build`.
2. **Reformat, or choose not to.** See [below](#reformatting-and-first-findings).
3. **Clear Error Prone's findings.** Fix them, or disable a check by name
   while you work through it.
4. **Switch to `STANDARD`.** Remove the property. SpotBugs and coverage join
   the gate; set `coverage.instruction` and `coverage.branch` to where the
   codebase is today, and raise them as tests are added.
5. **Mark packages `@NullMarked`, one at a time.** NullAway checks only
   those (see [Null checking](tools.md#null-checking-with-nullaway)).
6. **Move to `STRICT`** once the codebase is clean, or run it in CI only with
   `-Psterling.profile=strict`.

## Reformatting and first findings

Sterling enforces google-java-format 2.0, which formats some code
differently from 1.x and indents by 2. There are three ways in:

- **Reformat once** (the default). One commit touches every file, and
  `git blame` is told to skip it:

  ```bash
  ./gradlew stlFmt
  git commit -am "style: reformat with google-java-format 2.0"
  git rev-parse HEAD >> .git-blame-ignore-revs
  git config blame.ignoreRevsFile .git-blame-ignore-revs
  ```

- **AOSP style**, for a codebase that already indents by 4. The same
  formatter with 4-space indentation, so the reformat is far smaller:

  ```kotlin
  import dev.balakmran.sterling.FormatSettings

  sterling {
      format { style = FormatSettings.Style.AOSP }
  }
  ```

- **Ratchet**: format only what changes. `stlFmtCheck` and `stlFmt` then
  cover only files that differ from a git reference, so untouched files keep
  their layout until someone edits them:

  ```kotlin
  sterling {
      format { ratchetFrom = "origin/main" }
  }
  ```

  Spotless compares each file with the merge base of `HEAD` and the
  reference, not with the reference's tip, so commits on `main` since the
  branch started don't count. The reference must exist locally. A shallow CI
  checkout fails with Spotless's "No such reference" error, or can't find
  the merge base. Fetch the full history (`fetch-depth: 0` with
  `actions/checkout`), or fetch the branch with its history before the
  build:

  ```bash
  git fetch --unshallow origin main
  ```

AOSP and ratchet combine. Either can be dropped later: run `stlFmt` once
without it, as in the first option.

Error Prone will likely report a first batch of findings. Fix them, turn
individual checks off with `errorProne { disable("CheckName") }` while you
work through them, or
[suppress a single finding](tools.md#suppressing-one-finding).

## Already applying Spotless, SpotBugs or NullAway

A build that sets these tools up itself needs a few lines removed first, or
Sterling fails with a message naming each one. [Migrating to
Sterling](migrating.md) lists them, from Spring's NullAway snippet and from a
hand-written `build-logic`.

## Multi-module builds

Put Sterling in a convention plugin, so every module gets the same version
and settings. In `build-logic`, an included build:

```kotlin
// settings.gradle.kts
pluginManagement { includeBuild("build-logic") }
include("orders", "billing")
```

```kotlin
// build-logic/build.gradle.kts
plugins { `kotlin-dsl` }

repositories { gradlePluginPortal() }

dependencies {
    implementation("dev.balakmran.sterling:sterling-gradle-plugin:<version>")
}
```

```kotlin
// build-logic/src/main/kotlin/java-conventions.gradle.kts
plugins {
    java
    id("dev.balakmran.sterling")
}

repositories { mavenCentral() }

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

sterling {
    errorProne { disable("UnusedVariable") }
}
```

Each module applies the convention plugin, and can override any setting for
itself:

```kotlin
// billing/build.gradle.kts
plugins { id("java-conventions") }

sterling { coverage { instruction = 60 } }
```

`./gradlew stlCheck` at the root runs every module's gate, and
`./gradlew stlInfo` prints each module's settings. Coverage is measured and
checked per module; combining it across modules is planned for a later
release.

## Running the gate before you push

To check formatting before every commit and run the whole gate before every
push, with [prek](https://github.com/j178/prek) or pre-commit, add this
`.pre-commit-config.yaml`:

```yaml
repos:
  - repo: local
    hooks:
      - id: stl-fmt-check
        name: Check formatting
        entry: ./gradlew stlFmtCheck -q
        language: system
        pass_filenames: false
        stages: [pre-commit]
      - id: stl-check
        name: Run the Sterling gate
        entry: ./gradlew stlCheck -q
        language: system
        pass_filenames: false
        stages: [pre-push]
```

```bash
prek install --hook-type pre-commit --hook-type pre-push
```

To have [Claude Code](https://claude.com/claude-code) format and lint whenever
it finishes a turn, add a `Stop` hook to `.claude/settings.json`:

```json
{
  "hooks": {
    "Stop": [
      {
        "hooks": [{ "type": "command", "command": "./gradlew stlFmt stlLint -q" }]
      }
    ]
  }
}
```

## Using an unreleased build

Clone Sterling next to your project and include its build from your
project's `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("../sterling")
}
```

Then apply `id("dev.balakmran.sterling")` without a version.

To try it without editing `settings.gradle.kts`, pass the clone on the
command line instead; it replaces whatever version the build asks for:

```bash
./gradlew --include-build ../sterling build
```
