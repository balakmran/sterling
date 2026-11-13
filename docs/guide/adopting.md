# Adopting Sterling

## A path for an existing codebase

Each step is one pull request, and `main` stays green throughout:

1. **Apply Sterling on `LENIENT`.** `sterling.profile=lenient` in
   `gradle.properties`. The tools run on demand, but `check` doesn't run the
   gate, so nothing new fails `build` except Error Prone's error-level
   findings, which fail compilation in every profile.
2. **Reformat once.** See [below](#reformatting-and-first-findings).
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

google-java-format 2.0 formats some code differently from 1.x, and Sterling
enforces it. Reformat in one commit, then tell `git blame` to skip it:

```bash
./gradlew stlFmt
git commit -am "style: reformat with google-java-format 2.0"
git rev-parse HEAD >> .git-blame-ignore-revs
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

Error Prone will likely report a first batch of findings. Fix them, turn
individual checks off with `errorProne { disable("CheckName") }` while you
work through them, or
[suppress a single finding](tools.md#suppressing-one-finding).

## Already applying Spotless or SpotBugs

A hand-written `build-logic` often sets these tools up already. Sterling
applies both itself, so:

- **Spotless:** keeping `com.diffplug.spotless` is fine, at any version.
  Gradle loads one Spotless for the whole build script, the higher of yours
  and Sterling's (8.11.0). Remove `googleJavaFormat()` and
  `formatAnnotations()` from your `spotless { java { } }` block: Sterling adds
  both, google-java-format at its pinned version, and a second one fails.
  Other steps, such as a license header, work alongside them.
  `removeUnusedImports()`, `trimTrailingWhitespace()` and `endWithNewline()`
  on Java are redundant: google-java-format already does all three.
- **SpotBugs:** remove the `com.github.spotbugs` plugin. It registers
  `spotbugsMain` and attaches SpotBugs to `check`, both of which Sterling
  does. Your `spotbugs { }` settings keep working; a convention plugin that
  sets them applies `com.github.spotbugs-base` instead. Effort and report
  level set there win over Sterling's profile, so prefer
  `sterling { spotbugs { } }` for those.

A convention plugin like this, in `build-logic`:

```kotlin
import com.github.spotbugs.snom.Effort

plugins {
    java
    id("com.diffplug.spotless")
    id("com.github.spotbugs")
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

spotbugs {
    effort.set(Effort.MAX)
}
```

becomes this, with the tools' versions and wiring left to Sterling:

```kotlin
import dev.balakmran.sterling.SpotbugsSettings

plugins {
    java
    id("dev.balakmran.sterling")
}

sterling {
    spotbugs {
        effort = SpotbugsSettings.Effort.MAX
    }
}
```

`build-logic` then depends on Sterling instead of the Spotless and SpotBugs
plugins, and the projects that apply the convention plugin no longer apply
Sterling themselves:

```kotlin
// build-logic/build.gradle.kts
dependencies {
    implementation("dev.balakmran.sterling:sterling-gradle-plugin:<version>")
}
```

If the convention plugin keeps applying Spotless or SpotBugs, apply Sterling
after it, last in `plugins { }`. Then both clashes fail with a message from
Sterling that names the fix. Applied
first, Sterling can't step in: the error comes from the other plugin, as
"Cannot add task 'spotbugsMain'" under `com.github.spotbugs`, or
"Multiple steps with name 'google-java-format'" from Spotless. The same
Spotless error comes from a `spotless { }` block in the build script itself,
which always runs after Sterling. The fix is the same either way.

Spotless settings you set explicitly win over Sterling's, as with every
tool. `enforceCheck = true` attaches `spotlessCheck` to `check` beside
Sterling's gate, and a `lineEndings` other than `PRESERVE` can read git
settings while configuring, which stops the configuration cache from being
reused.

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
