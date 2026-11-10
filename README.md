# Sterling

Opinionated, configurable code-quality enforcement for Gradle Java projects.

Apply one plugin and `./gradlew build` formats-checks, lints, null-checks,
bug-scans and coverage-verifies your code:

| Tool | What it does | Sterling task |
|---|---|---|
| [google-java-format](https://github.com/google/google-java-format), via [Spotless](https://github.com/diffplug/spotless) | Formats Java sources | `stlFmt`, `stlFmtCheck` |
| [Error Prone](https://errorprone.info) | Finds common bugs at compile time | `stlLint` |
| [NullAway](https://github.com/uber/NullAway) | Finds possible `NullPointerException`s at compile time | `stlLint` |
| [SpotBugs](https://spotbugs.github.io) | Finds bug patterns in bytecode | `stlSec` |
| [JaCoCo](https://www.jacoco.org/jacoco/) | Measures and enforces test coverage | `stlCov` |

One setting, the **profile**, decides how strict all of them are. Every tool
can be switched off or tuned on its own.

> **Status: pre-release.** Versions before 0.1.0 are alphas, and the DSL may
> still change. Until a version is on the Gradle Plugin Portal, try it from a
> local clone (see [Trying it before release](#trying-it-before-release)).

## Requirements

- **Java 21 or later**, for both:
  - the project's toolchain, since Error Prone runs inside javac; and
  - the JDK the Gradle daemon runs on, since google-java-format runs inside it.
    Pin it with `./gradlew updateDaemonJvm --jvm-version=21` (or 25).

  Sterling fails fast with a clear message if either is older. Java 17 isn't
  supported. A project that must ship Java 17 bytecode can still compile with
  a newer JDK and `options.release = 17`.
- **Gradle 9.0 or later.**
- **`mavenCentral()`** in the project's repositories. Sterling downloads
  google-java-format, Error Prone, NullAway, JSpecify, SpotBugs and the JaCoCo
  agent from there, only for the tools that are on.

## Installation

<!-- x-release-please-start-version -->
```kotlin
plugins {
    java
    id("dev.balakmran.sterling.java") version "0.1.0-alpha.2"
}

repositories { mavenCentral() }
```
<!-- x-release-please-end -->

That's all most projects need. With the default `STANDARD` profile,
`./gradlew build` now runs the whole gate. Sterling never applies `java` for
you, and works whether it's applied before or after it.

`dev.balakmran.sterling` is the base plugin: the `sterling { }` block and the
tasks, without any tools. Use it only to share configuration in a build where
not every project is Java.

### Adopting it in an existing project

google-java-format 2.0 formats some code differently from 1.x, and Sterling
enforces it. Reformat in one commit, then tell `git blame` to skip it:

```bash
./gradlew stlFmt
git commit -am "style: reformat with google-java-format 2.0"
git rev-parse HEAD >> .git-blame-ignore-revs
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

Error Prone will likely report a first batch of findings. Fix them, or turn
individual checks off with `errorProne { disable("CheckName") }` while you
work through them. The `LENIENT` profile is a gentler start: the tools run on
demand, but `build` doesn't fail on the gate.

## Tasks

All in the `sterling` group:

| Task | What it does |
|---|---|
| `stlFmt` | Formats Java sources in place. The only task that changes code. |
| `stlFmtCheck` | Fails if any source isn't formatted. |
| `stlLint` | Compiles main and test sources with Error Prone and NullAway. |
| `stlSec` | Runs SpotBugs on the main classes. |
| `stlCov` | Runs the tests and fails if coverage is below the thresholds. |
| `stlCheck` | Runs `stlFmtCheck`, `stlLint`, `stlSec` and `stlCov`. `check` depends on it unless `attachToCheck` is off. |
| `stlInfo` | Prints the resolved configuration. |

A tool that's off makes its task do nothing. The tools' own tasks
(`spotlessApply`, `spotbugsMain`, `jacocoTestReport` and so on) keep working,
but none of them attaches itself to `check`: only `stlCheck` does.

`./gradlew stlInfo` shows what Sterling will do:

```
Sterling 0.1.0
Profile:              STANDARD
Gate wired to check:  yes
Daemon JVM:           25
Java toolchain:       25
Formatting:           on   (google-java-format 2.0.0)
Error Prone:          on   (2.52.0; warnings are not errors; 1 check disabled)
NullAway:             on   (0.14.3; JSpecify 1.0.1; @NullMarked code only)
SpotBugs:             on   (4.10.5)
Coverage:             on   (instruction >= 80%, branch >= 70%; 1 exclude)
```

## Profiles

| Setting | `LENIENT` | `STANDARD` (default) | `STRICT` |
|---|---|---|---|
| `attachToCheck` | false | true | true |
| `format.enabled` | true | true | true |
| `errorProne.enabled` | true | true | true |
| `errorProne.warningsAsErrors` | false | false | true |
| `nullAway.enabled` | true | true | true |
| `spotbugs.enabled` | false | true | true |
| `coverage.enabled` | false | true | true |
| `coverage.instruction` | 0 | 80 | 95 |
| `coverage.branch` | 0 | 70 | 85 |

- `LENIENT` still compiles with Error Prone and NullAway, so their
  error-level findings still fail the build. It only stops `check` from
  running the gate.
- `STRICT`'s `warningsAsErrors` adds `-Werror`, which also fails on javac's own
  warnings, such as `[removal]`.
- NullAway is on in every profile because it does nothing until a package opts
  in (see [Null checking](#null-checking-with-nullaway)).

### Choosing the profile without editing the build script

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

Every setting defaults from the profile. Set any of them to override that
setting alone:

```kotlin
import dev.balakmran.sterling.Profile

sterling {
    profile = Profile.STANDARD          // LENIENT | STANDARD | STRICT
    attachToCheck = true                // whether check runs stlCheck

    format {
        enabled = true
    }
    errorProne {
        enabled = true
        warningsAsErrors = false        // adds -Werror to javac
        disable("UnusedVariable")       // turns checks off by name
    }
    nullAway {
        enabled = true                  // needs Error Prone
    }
    spotbugs {
        enabled = true
        excludeFilter = file("config/spotbugs-exclude.xml") // added to Sterling's
    }
    coverage {
        enabled = true
        instruction = 80                // minimum percent, 0 to 100
        branch = 70                     // minimum percent, 0 to 100
        excludes.add("com/example/Application*") // class files to leave out
    }
}
```

In Groovy build scripts the block is the same, with
`profile = dev.balakmran.sterling.Profile.STANDARD` and single-quoted strings.
A threshold outside 0 to 100, or an unknown `sterling.profile`, fails the
build with a message naming the setting.

## Tool notes

### Formatting

`stlFmt` and `stlFmtCheck` cover every source set. Sources under any
`build/generated` directory (OpenAPI generators, annotation processors,
Spring AOT) are left alone. Each file keeps the line endings it has; enforce
those with `.gitattributes` or Spotless's own `lineEndings` setting.

### Error Prone

Error Prone runs inside every Java compilation. Error-level findings always
fail it; warnings fail it only with `warningsAsErrors`. Sources under
`build/generated` are skipped entirely, and warnings in code annotated
`@Generated` are ignored.

### Null checking with NullAway

NullAway only checks packages marked `@NullMarked`, so you adopt it one package
at a time:

```java
// src/main/java/com/example/orders/package-info.java
@NullMarked
package com.example.orders;

import org.jspecify.annotations.NullMarked;
```

Inside that package, anything that may be null must be annotated `@Nullable`,
and NullAway fails compilation where such a value is used without a check.

Sterling adds [JSpecify](https://jspecify.dev) to `compileOnly` and
`testCompileOnly` while NullAway is on, so the annotations work without a
dependency of your own. Spring Boot 4 apps get it through `spring-core` too.
A **library** whose public API uses these annotations should still declare
`api("org.jspecify:jspecify:1.0.1")`, so Kotlin callers and other tools see
them.

### SpotBugs

`stlSec` analyses the main classes, not tests, and writes HTML, XML and SARIF
reports under `build/reports/spotbugs`. Sterling's default exclude filter
skips three patterns that Spring constructor injection and records trigger
in ordinary code: `EI_EXPOSE_REP`, `EI_EXPOSE_REP2` and `CT_CONSTRUCTOR_THROW`.

It also skips every class compiled from generated sources: those in source
directories under `build/generated` (OpenAPI generators and the like) and
annotation processors' output (MapStruct and others). Spring AOT's classes
aren't analysed at all.

`spotbugs.excludeFilter` adds your own exclusions to Sterling's, for example
for generated code that lives somewhere else:

```xml
<FindBugsFilter>
  <Match>
    <Package name="~com\.example\.api\.generated(\..*)?"/>
  </Match>
</FindBugsFilter>
```

### Coverage

Coverage is measured on the `test` task. After it runs, `jacocoTestReport`
writes HTML and XML reports, and `stlCov` checks them against the
thresholds. With coverage off, tests run without the JaCoCo agent at all.

JaCoCo already ignores classes and methods annotated with any annotation whose
name contains `Generated` and that's kept in the class file, such as Lombok's
`@Generated` or one of your own. It's Java's equivalent of
`# pragma: no cover`. Annotations kept only in source, like MapStruct's or
`jakarta.annotation.Generated`, aren't seen; use `coverage.excludes` for those.

## Configuring the tools directly

Sterling covers the common settings. For anything else, configure the tool
through its own DSL; that configuration applies alongside Sterling's.

```kotlin
import com.github.spotbugs.snom.Effort
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

// SpotBugs analysis effort.
tasks.named<SpotBugsTask>("spotbugsMain") {
    effort = Effort.MAX
}
```

Sterling already adds Spotless's google-java-format step, so calling
`googleJavaFormat()` again fails with "Multiple steps with name
'google-java-format'". Add other steps instead.

Pointing a tool at a newer version than Sterling pins (for example
`spotbugs { toolVersion = "..." }`) works too, but isn't tested yet.

## Git hooks

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

## Trying it before release

Clone Sterling next to your project and include its build from your
project's `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("../sterling")
}
```

Then apply `id("dev.balakmran.sterling.java")` without a version.

## Building Sterling

Sterling is written in Java; its build scripts use the Gradle Kotlin DSL.
Building it needs JDK 21; its functional tests also need JDK 17, to check the
"needs Java 21" errors, and JDK 25, a consumer toolchain they test. Gradle
finds installed JDKs, including SDKMAN's, and doesn't download any.

```bash
./gradlew build                 # compile, unit tests, Javadoc, plugin validation, dependency checks
./gradlew functionalTest        # tests that run real builds with TestKit
./gradlew spotlessApply         # format Sterling's own code
```

Functional tests run consumer builds on the latest Gradle with a Java 21
toolchain by default. CI also runs Gradle 9.0.0 and Java 25; to run one of
those combinations locally (JDK 25 must be installed):

```bash
./gradlew functionalTest -Psterling.test.gradle=9.0.0 -Psterling.test.java=25
```

## License

[Apache License 2.0](LICENSE)
