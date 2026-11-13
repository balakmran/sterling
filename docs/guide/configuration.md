# Configuring Sterling

Every setting defaults from the profile, and any of them can be set on its
own in the `sterling { }` block.

## Profile defaults

| Setting | `LENIENT` | `STANDARD` (default) | `STRICT` |
|---|---|---|---|
| `attachToCheck` | false | true | true |
| `format.enabled` | true | true | true |
| `errorProne.enabled` | true | true | true |
| `errorProne.warningsAsErrors` | false | false | true |
| `nullAway.enabled` | true | true | true |
| `nullAway.jspecifyMode` | false | false | true |
| `spotbugs.enabled` | false | true | true |
| `spotbugs.effort` | `DEFAULT` | `DEFAULT` | `MAX` |
| `spotbugs.reportLevel` | `HIGH` | `MEDIUM` | `MEDIUM` |
| `coverage.enabled` | false | true | true |
| `coverage.instruction` | 0 | 80 | 95 |
| `coverage.branch` | 0 | 70 | 85 |

## What the profiles mean

- `LENIENT` still compiles with Error Prone and NullAway, so their
  error-level findings still fail the build. It only stops `check` from
  running the gate.
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
import dev.balakmran.sterling.Profile
import dev.balakmran.sterling.SpotbugsSettings

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
        jspecifyMode = false            // also checks generics, arrays, varargs
    }
    spotbugs {
        enabled = true
        effort = SpotbugsSettings.Effort.DEFAULT       // MIN | LESS | DEFAULT | MORE | MAX
        reportLevel = SpotbugsSettings.Confidence.MEDIUM // LOW | MEDIUM | HIGH
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

A threshold outside 0 to 100, or an unknown `sterling.profile`, fails the
build with a message naming the setting.

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
        excludes.add('com/example/Application*')
    }
}
```

### Settings reference

| Setting | Type | Default |
|---|---|---|
| `profile` | `Profile`: `LENIENT`, `STANDARD` or `STRICT` | The `sterling.profile` property, else `STANDARD` |
| `attachToCheck` | Boolean | From the profile |
| `format.enabled` | Boolean | true |
| `errorProne.enabled` | Boolean | true |
| `errorProne.warningsAsErrors` | Boolean | From the profile |
| `errorProne.disabledChecks` | Set of check names | Empty |
| `errorProne.disable(...)` | Adds check names to `disabledChecks` | |
| `nullAway.enabled` | Boolean | true (needs Error Prone) |
| `nullAway.jspecifyMode` | Boolean | From the profile |
| `spotbugs.enabled` | Boolean | From the profile |
| `spotbugs.effort` | `SpotbugsSettings.Effort`: `MIN`, `LESS`, `DEFAULT`, `MORE` or `MAX` | From the profile |
| `spotbugs.reportLevel` | `SpotbugsSettings.Confidence`: `LOW`, `MEDIUM` or `HIGH` | From the profile |
| `spotbugs.excludeFilter` | File | None |
| `coverage.enabled` | Boolean | From the profile |
| `coverage.instruction` | Percent, 0 to 100 | From the profile |
| `coverage.branch` | Percent, 0 to 100 | From the profile |
| `coverage.excludes` | List of class-file patterns | Empty |

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

Sterling already adds Spotless's `googleJavaFormat()` and
`formatAnnotations()` steps, so adding either again fails; add other steps
instead. [Already applying Spotless or SpotBugs](adopting.md#already-applying-spotless-or-spotbugs)
covers a `build-logic` that sets these tools up itself.

Pointing a tool at a newer version than Sterling pins (for example
`spotbugs { toolVersion = "..." }`) works too, but isn't tested yet.
