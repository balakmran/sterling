# Sterling

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/dev.balakmran.sterling?include_prereleases)](https://plugins.gradle.org/plugin/dev.balakmran.sterling)
[![CI](https://github.com/balakmran/sterling/actions/workflows/ci.yml/badge.svg?event=pull_request)](https://github.com/balakmran/sterling/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

One code-quality gate for Java 21+ Gradle builds: google-java-format, Error Prone, NullAway with JSpecify, SpotBugs and JaCoCo, at versions tested together, run by stlCheck and tuned by one profile.

Apply one plugin, and `./gradlew build` checks formatting, finds bugs and
possible `NullPointerException`s at compile time, scans the bytecode, and
enforces test coverage:

| Tool | What it does | Sterling task |
|---|---|---|
| [google-java-format](https://github.com/google/google-java-format), via [Spotless](https://github.com/diffplug/spotless) | Formats Java sources | `stlFmt`, `stlFmtCheck` |
| [Error Prone](https://errorprone.info) | Finds common bugs at compile time | `stlLint` |
| [NullAway](https://github.com/uber/NullAway), with [JSpecify](https://jspecify.dev) annotations | Finds possible `NullPointerException`s at compile time, in code marked `@NullMarked` | `stlLint` |
| [SpotBugs](https://spotbugs.github.io) | Finds bug patterns in bytecode | `stlSec` |
| [JaCoCo](https://www.jacoco.org/jacoco/) | Measures and enforces test coverage | `stlCov` |

One setting, the [**profile**](#profiles), decides how strict all of them are. Every tool
can be switched off or tuned on its own.

## Why Sterling

- **One plugin instead of a hand-written `build-logic`.** Five tools, wired
  so that only one gate attaches to `check`, with no `afterEvaluate`, and
  compatible with the configuration cache.
- **Versions tested together.** Each Sterling release pins every tool and is
  tested on Gradle 9.0 and the latest, with Java 21 and 25. A tool upgrade
  that can fail your build arrives as a minor release, with a note saying so.
- **One gate for people and coding agents.** `stlFmt` fixes formatting, and
  `stlCheck` answers "is this ready?" the same way locally and in CI.

Spring's own `io.spring.nullability` plugin sets up Error Prone and NullAway
only. Sterling adds formatting, SpotBugs and coverage, and one profile for
all five.

> **Status: pre-release.** Versions before 0.1.0 are alphas on the Gradle
> Plugin Portal, and the DSL may still change. What comes after 0.1.0 is in
> the [roadmap](ROADMAP.md).

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
    id("dev.balakmran.sterling") version "0.1.0-alpha.6"
}

repositories { mavenCentral() }
```
<!-- x-release-please-end -->

That's all most projects need. With the default `STANDARD` profile,
`./gradlew build` now runs the whole gate. Sterling never applies `java` for
you, and works whether it's applied before or after it. In a project without
`java`, the tools stay off and the `stl*` tasks have nothing to run. In a
Groovy build script, use `id 'dev.balakmran.sterling' version '...'`; the
`sterling { }` block is the same.

### First run

```bash
./gradlew stlInfo    # what Sterling will check, and how strictly
./gradlew stlFmt     # format every source once
./gradlew build      # compile, test, and run the gate
```

Adding it to a project that already has code, or that sets up Spotless or
SpotBugs itself? See [Adopting Sterling](docs/guide/adopting.md).

### Spring Boot

Apply Sterling next to the Spring Boot plugin. Tests rarely run the
application class's `main` method, so leave it out of coverage:

```kotlin
sterling {
    coverage {
        excludes.add("com/example/Application*")
    }
}
```

### Multi-module builds

Apply Sterling in every module that has Java code, in each build script or
from a convention plugin in `build-logic`. Each module gets its own `stl*`
tasks and gate, and `./gradlew stlCheck` at the root runs them all.

## Profiles

| | `LENIENT` | `STANDARD` (default) | `STRICT` |
|---|---|---|---|
| `check` runs the gate | no | yes | yes |
| SpotBugs and coverage | off | on | on |
| Coverage: instructions, branches | off | 80%, 70% | 95%, 85% |
| Compiler warnings fail the build | no | no | yes |
| NullAway's JSpecify mode | off | off | on |
| SpotBugs effort, lowest confidence reported | default, high | default, medium | max, medium |

Formatting, Error Prone and NullAway are on in every profile. Pick one with
`sterling { profile = Profile.STRICT }`, or `-Psterling.profile=strict`
without editing the build script. Every default, and every setting you can
change on its own: [Configuring Sterling](docs/guide/configuration.md).

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
| `stlInfo` | Prints Sterling's resolved settings. |

A tool that's off makes its task do nothing. The tools' own tasks
(`spotlessApply`, `spotbugsMain`, `jacocoTestReport` and so on) keep working,
but none of them attaches itself to `check`: only `stlCheck` does.

`./gradlew stlInfo` prints Sterling's settings, after the profile, the
`sterling.profile` property and the `sterling { }` block have been applied.
Anything set through a tool's own DSL (see
[Configuring the tools directly](docs/guide/configuration.md#configuring-the-tools-directly)), such as
`spotbugs { effort = ... }` or `options.errorprone { }`, still applies but
isn't shown:

```
Sterling 0.1.0
Profile:              STANDARD
Gate wired to check:  yes
Daemon JVM:           25
Java toolchain:       25
Formatting:           on   (google-java-format 2.0.0)
Error Prone:          on   (2.52.0; warnings are not errors; 1 check disabled)
NullAway:             on   (0.14.3; JSpecify 1.0.1; @NullMarked code only)
SpotBugs:             on   (4.10.5; effort default; medium confidence and up)
Coverage:             on   (instruction >= 80%, branch >= 70%; 1 exclude)
```

## Documentation

- [Adopting Sterling](docs/guide/adopting.md): a first reformat, projects
  that already apply Spotless or SpotBugs, git hooks, unreleased builds.
- [Configuring Sterling](docs/guide/configuration.md): profiles, the
  `sterling { }` block, every setting, and the tools' own DSLs.
- [The tools](docs/guide/tools.md): pinned versions, what an upgrade can
  change, and notes on each tool.
- [Roadmap](ROADMAP.md) and [changelog](CHANGELOG.md).
- [Contributing](CONTRIBUTING.md): building and testing Sterling.

## License

[Apache License 2.0](LICENSE)
