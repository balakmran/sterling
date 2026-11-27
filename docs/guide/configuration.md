# Configuring Sterling

Every setting defaults from the profile, and any of them can be set on its
own in the `sterling { }` block.

## Profile defaults

| Setting | `LENIENT` | `STANDARD` (default) | `STRICT` |
|---|---|---|---|
| `attachToCheck` | false | true | true |
| `format.enabled` | true | true | true |
| `format.style` | `GOOGLE` | `GOOGLE` | `GOOGLE` |
| `format.formatAnnotations` | false | false | false |
| `errorProne.enabled` | true | true | true |
| `errorProne.warningsAsErrors` | false | false | true |
| `errorProne.errorsAsWarnings` | true | false | false |
| `nullAway.enabled` | true | true | true |
| `nullAway.jspecifyMode` | false | false | true |
| `spotbugs.enabled` | false | true | true |
| `spotbugs.effort` | `DEFAULT` | `DEFAULT` | `MAX` |
| `spotbugs.reportLevel` | `HIGH` | `MEDIUM` | `MEDIUM` |
| `coverage.enabled` | false | true | true |
| `coverage.instruction` | 0 | 80 | 95 |
| `coverage.branch` | 0 | 70 | 85 |

## What the profiles mean

- `LENIENT` still compiles with Error Prone and NullAway, but reports their
  error-level findings as warnings (`errorsAsWarnings`), so nothing new fails
  the build. It also stops `check` from running the gate.
- `STRICT`'s `warningsAsErrors` adds `-Werror`, which also fails on javac's own
  warnings, such as `[removal]`.
- NullAway is on in every profile because it does nothing until a package
  opts in (see [Null checking](tools.md#null-checking-with-nullaway)). Its
  JSpecify mode is on only in `STRICT`.
- SpotBugs reports medium- and high-confidence findings, SpotBugs' own
  default, and only high-confidence ones under `LENIENT`. No profile reports
  low-confidence findings: on Spring code they're mostly noise, such as a
  `@Bean` method declared `throws Exception` because Spring's API requires it.
  `STRICT` analyses at maximum effort.

## Choosing the profile without editing the build script

Set the `sterling.profile` Gradle property, in any case. A project keeps its
choice in `gradle.properties`, and CI can override it:

```properties
sterling.profile=standard
```

```bash
./gradlew build -Psterling.profile=strict
```

A `profile = ...` in the build script wins over the property, so leave it out
of the script if CI should choose.

## Settings

Set any setting to override that setting alone. The values below are
`STANDARD`'s defaults:

```kotlin
import dev.balakmran.sterling.FormatSettings
import dev.balakmran.sterling.Profile
import dev.balakmran.sterling.SpotbugsSettings

sterling {
    profile = Profile.STANDARD          // LENIENT | STANDARD | STRICT
    attachToCheck = true                // whether check runs stlCheck

    format {
        enabled = true
        style = FormatSettings.Style.GOOGLE // GOOGLE | AOSP (4-space indentation)
        formatAnnotations = false       // true adds Spotless's formatAnnotations() step
        // ratchetFrom = "origin/main"  // unset: formats every file, not just changed ones
    }
    errorProne {
        enabled = true
        warningsAsErrors = false        // adds -Werror to javac
        errorsAsWarnings = false        // reports Error Prone's errors as warnings; true in LENIENT
        disable("UnusedVariable")       // turns checks off by name
    }
    nullAway {
        enabled = true                  // needs Error Prone
        jspecifyMode = false            // also checks generics, arrays, varargs
    }
    spotbugs {
        enabled = true
        effort = SpotbugsSettings.Effort.DEFAULT       // MIN | LESS | DEFAULT | MORE | MAX
        reportLevel = SpotbugsSettings.Confidence.MEDIUM // LOW | MEDIUM | HIGH
        excludeFilter = file("config/spotbugs-exclude.xml") // added to Sterling's
        // baselineFile = file("config/spotbugs/baseline.xml") // unset: every finding counts
    }
    coverage {
        enabled = true
        instruction = 80                // minimum percent, 0 to 100
        branch = 70                     // minimum percent, 0 to 100
        testTasks = setOf("test", "integrationTest") // default: every Test task
        excludes.add("com/example/Legacy*") // class files to leave out
    }
}
```

A threshold outside 0 to 100, a `coverage.testTasks` name that isn't a
`Test` task, or an unknown `sterling.profile`, fails the build with a message
naming the setting.

An empty `format.ratchetFrom` fails too; leave it unset to format every file.

`format.style`, `format.formatAnnotations` and `format.ratchetFrom` are read once, when Spotless
configures `spotlessJava`, normally after every build script has run. A
build that realises `spotlessJava` early and sets either later, in an `afterEvaluate` block for
example, fails with Gradle's "final" error rather than having the value
ignored.

In a Groovy build script, the block is the same apart from quotes:

```groovy
import dev.balakmran.sterling.Profile
import dev.balakmran.sterling.SpotbugsSettings

plugins {
    id 'java'
    id 'dev.balakmran.sterling' version '<version>'
}

sterling {
    profile = Profile.STRICT
    errorProne {
        disable('UnusedVariable')
    }
    spotbugs {
        effort = SpotbugsSettings.Effort.MAX
    }
    coverage {
        excludes.add('com/example/Legacy*')
    }
}
```

### Settings reference

| Setting | Type | Default |
|---|---|---|
| `profile` | `Profile`: `LENIENT`, `STANDARD` or `STRICT` | The `sterling.profile` property, else `STANDARD` |
| `attachToCheck` | Boolean | From the profile |
| `format.enabled` | Boolean | true |
| `format.style` | `FormatSettings.Style`: `GOOGLE` or `AOSP` | `GOOGLE` |
| `format.formatAnnotations` | Boolean | false |
| `format.ratchetFrom` | A git reference, such as `origin/main` | Unset: every file is formatted |
| `errorProne.enabled` | Boolean | true |
| `errorProne.warningsAsErrors` | Boolean | From the profile |
| `errorProne.errorsAsWarnings` | Boolean | From the profile |
| `errorProne.disabledChecks` | Set of check names | Empty |
| `errorProne.disable(...)` | Adds check names to `disabledChecks` | |
| `nullAway.enabled` | Boolean | true (needs Error Prone) |
| `nullAway.jspecifyMode` | Boolean | From the profile |
| `spotbugs.enabled` | Boolean | From the profile |
| `spotbugs.effort` | `SpotbugsSettings.Effort`: `MIN`, `LESS`, `DEFAULT`, `MORE` or `MAX` | From the profile |
| `spotbugs.reportLevel` | `SpotbugsSettings.Confidence`: `LOW`, `MEDIUM` or `HIGH` | From the profile |
| `spotbugs.excludeFilter` | File | None |
| `spotbugs.baselineFile` | File: a SpotBugs XML report whose findings don't fail `stlSec` | None |
| `coverage.enabled` | Boolean | From the profile |
| `coverage.instruction` | Percent, 0 to 100 | From the profile |
| `coverage.branch` | Percent, 0 to 100 | From the profile |
| `coverage.testTasks` | Set of `Test` task names | Empty, meaning every `Test` task |
| `coverage.excludes` | List of class-file patterns | Empty |

## Reading the configuration as JSON

`./gradlew -q stlInfo --json` prints the same configuration as one line of
JSON per project, for scripts, CI and coding agents. `-q` leaves only the JSON
lines on standard output:

```json
{"schemaVersion":1,"sterling":"0.2.0","project":":","profile":"STANDARD","profileProperty":"standard","attachToCheck":true,"daemonJvm":25,"javaToolchain":{"active":true,"version":25},"format":{"enabled":true,"style":"GOOGLE","formatAnnotations":false,"ratchetFrom":null,"versions":{"google-java-format":"2.0.0","spotless":"8.11.0"}},"errorProne":{"enabled":true,"version":"2.52.0","versionSource":"sterling","warningsAsErrors":false,"errorsAsWarnings":false,"disabledChecks":["UnusedVariable"]},"nullAway":{"enabled":true,"active":true,"version":"0.14.3","versionSource":"sterling","jspecify":"1.0.1","jspecifySource":"sterling","jspecifyMode":false},"spotbugs":{"enabled":true,"version":"4.10.5","versionSource":"sterling","effort":"DEFAULT","reportLevel":"MEDIUM","excludeFilter":null,"baselineFile":null,"reports":{"sarif":"build/reports/spotbugs/main.sarif","xml":"build/reports/spotbugs/main.xml"}},"coverage":{"enabled":true,"version":"0.8.16","versionSource":"sterling","instruction":80,"branch":70,"excludes":[],"reports":{"xml":"build/reports/jacoco/test/jacocoTestReport.xml"}}}
```

Keys are named after the settings above. A version the project overrides
is the project's, or `null` when it declared the dependency without a
version, as through a platform. Paths are relative to the project
directory, with `/` separators; a file on another drive than the project is
given as an absolute path.

| Key | Value |
|---|---|
| `schemaVersion` | The format's version, 1 |
| `sterling` | Sterling's version |
| `project` | The project's path, such as `:` or `:api` |
| `profile` | The resolved profile |
| `profileProperty` | The `sterling.profile` property as given, or `null` |
| `attachToCheck` | Boolean |
| `daemonJvm` | The Java version the Gradle daemon runs on |
| `javaToolchain` | `{"active": true, "version": 25}`, or `{"active": false, "reason": "..."}` without the `java` plugin |
| `javaToolchain.active` | Boolean |
| `javaToolchain.version` | The toolchain's Java version |
| `javaToolchain.reason` | Why the Java tools aren't active |
| `format.enabled` | Boolean |
| `format.style` | `GOOGLE` or `AOSP` |
| `format.formatAnnotations` | Boolean |
| `format.ratchetFrom` | The git reference, or `null` |
| `format.versions` | Object |
| `format.versions.google-java-format` | Version |
| `format.versions.spotless` | Version |
| `errorProne.enabled` | Boolean |
| `errorProne.version` | Version |
| `errorProne.versionSource` | `"sterling"` for Sterling's pin, `"project"` for a [project override](tools.md#using-a-different-tool-version) |
| `errorProne.warningsAsErrors` | Boolean |
| `errorProne.errorsAsWarnings` | Boolean |
| `errorProne.disabledChecks` | Check names, sorted |
| `nullAway.enabled` | Boolean |
| `nullAway.active` | Whether NullAway runs: `enabled` and Error Prone on |
| `nullAway.version` | Version |
| `nullAway.versionSource` | `"sterling"` or `"project"` |
| `nullAway.jspecify` | JSpecify's version |
| `nullAway.jspecifySource` | `"sterling"` or `"project"` |
| `nullAway.jspecifyMode` | Boolean |
| `spotbugs.enabled` | Boolean |
| `spotbugs.version` | Version |
| `spotbugs.versionSource` | `"sterling"` or `"project"` |
| `spotbugs.effort` | `MIN` to `MAX` |
| `spotbugs.reportLevel` | `LOW`, `MEDIUM` or `HIGH` |
| `spotbugs.excludeFilter` | The project's filter, or `null` |
| `spotbugs.baselineFile` | The baseline's path relative to the project, or `null` |
| `spotbugs.reports` | Object; absent without the `java` plugin |
| `spotbugs.reports.sarif` | Path |
| `spotbugs.reports.xml` | Path |
| `coverage.enabled` | Boolean |
| `coverage.version` | JaCoCo's version |
| `coverage.versionSource` | `"sterling"` or `"project"` |
| `coverage.instruction` | Percent |
| `coverage.branch` | Percent |
| `coverage.excludes` | Class-file patterns |
| `coverage.testTasks` | The test tasks that count: the setting, or every `Test` task when it's empty; `[]` without the `java` plugin |
| `coverage.excludesMainClass` | Whether the Spring Boot main class is left out: true with the `org.springframework.boot` plugin |
| `coverage.reports` | Object; absent without the `java` plugin |
| `coverage.reports.xml` | Path |

The schema only grows within a version: a new key keeps `schemaVersion` at 1.
Removing or renaming a key, or changing a value's type, raises it, and the
release notes call it a breaking change. A reader should check
`schemaVersion` before anything else.

## Configuring the tools directly

Sterling covers the common settings. For anything else, configure the tool
through its own DSL; that configuration applies alongside Sterling's:

| Tool | DSL | Docs |
|---|---|---|
| google-java-format | `spotless { java { } }` | [Spotless Gradle plugin](https://github.com/diffplug/spotless/tree/main/plugin-gradle) |
| Error Prone, NullAway | `options.errorprone { }` on `JavaCompile` | [Gradle Error Prone plugin](https://github.com/tbroyer/gradle-errorprone-plugin) |
| SpotBugs | `spotbugs { }`, or the `spotbugsMain` task | [SpotBugs Gradle plugin](https://github.com/spotbugs/spotbugs-gradle-plugin) |
| JaCoCo | `jacoco { }`, `jacocoTestReport`, `jacocoTestCoverageVerification` | [Gradle's JaCoCo plugin](https://docs.gradle.org/current/userguide/jacoco_plugin.html) |

For example:

```kotlin
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.errorprone

// Another Spotless step: a license header on every Java file.
spotless {
    java {
        licenseHeader("/* Copyright (C) Example Corp */")
    }
}

// Error Prone: make a warning-level check fail compilation.
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        error("MissingOverride")
    }
}

// SpotBugs: more memory for a large codebase.
tasks.named<SpotBugsTask>("spotbugsMain") {
    maxHeapSize = "2g"
}
```

Sterling already adds Spotless's `googleJavaFormat()` step, so adding it again
fails (and `formatAnnotations()` too, with `format.formatAnnotations = true`);
add other steps instead. [Migrating to Sterling](migrating.md) covers a `build-logic` that sets these
tools up itself.

To run a tool at another version than Sterling pins, see
[Using a different tool version](tools.md#using-a-different-tool-version).
