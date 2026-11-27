# Migrating to Sterling

Sterling applies Spotless, Error Prone, SpotBugs and JaCoCo itself and sets
the options each needs, so a build that already does that has a few lines to
remove. The table says which; the sections below explain the two common
starting points: [Spring's NullAway snippet](#from-springs-nullaway-snippet)
and [a hand-written `build-logic`](#from-a-hand-written-build-logic).

## What to remove

| In your build | What happens | Instead |
|---|---|---|
| The `com.github.spotbugs` plugin | Fails: it registers `spotbugsMain` and attaches SpotBugs to `check`, as Sterling does | Remove it. A convention plugin that must configure SpotBugs applies `com.github.spotbugs-base` |
| `spotbugs { effort }` and `spotbugs { reportLevel }` | Win over Sterling's profile | `sterling { spotbugs { effort; reportLevel } }` |
| `spotbugs { excludeFilter }` | Replaces Sterling's default filter | `sterling { spotbugs { excludeFilter } }`, which adds to it |
| `googleJavaFormat()` in `spotless { java { } }` | Fails: "Multiple steps with name 'google-java-format'" | Remove it; Sterling adds it at its pinned version |
| `formatAnnotations()` in `spotless { java { } }` | Works alongside Sterling's, unless `format.formatAnnotations = true`, which fails the same way | Keep it, or remove it to get google-java-format's own layout |
| `removeUnusedImports()`, `trimTrailingWhitespace()` and `endWithNewline()` on Java | Redundant: google-java-format does all three | Remove them |
| Other Spotless steps, such as a license header | Work alongside Sterling's | Keep them |
| `errorprone("com.google.errorprone:error_prone_core:<version>")`, `errorprone("com.uber.nullaway:nullaway:<version>")` and `compileOnly("org.jspecify:jspecify:<version>")` | A version other than Sterling's is a [project override](tools.md#using-a-different-tool-version) | Remove them, unless you want a newer version than Sterling pins |
| `options.errorprone` settings for `NullAway`, `NullAway:OnlyNullMarked`, `NullAway:JSpecifyMode`, `NullAway:CheckContracts`, `NullAway:HandleTestAssertionLibraries` and `NullAway:CustomContractAnnotations` | Redundant or conflicting: yours wins over Sterling's | Remove them; `nullAway.jspecifyMode` turns JSpecify mode on |
| `disableAllChecks`, and `disable("CheckName")` on `JavaCompile` | Sterling runs all of Error Prone by design | Remove `disableAllChecks`; `errorProne { disable("CheckName") }` for individual checks |
| JaCoCo `violationRules` | A second set of thresholds, checked by `jacocoTestCoverageVerification` | `coverage.instruction` and `coverage.branch` |
| JaCoCo class excludes | Still apply | `coverage.excludes`; classes from generated sources and the Spring Boot main class are left out without them |
| `check { dependsOn(...) }` on a JaCoCo or SpotBugs task | Runs the tool a second time beside `stlCheck`, which `check` already depends on | Remove it |
| Test tasks wired to `jacocoTestReport` | `stlCov` runs every `Test` task and reports them together | Remove the wiring, or narrow `coverage.testTasks` |
| Checkstyle and PMD | Not managed by Sterling | Keep them |

Spotless settings you set explicitly win over Sterling's, as with every tool.
`enforceCheck = true` attaches `spotlessCheck` to `check` beside Sterling's
gate, and a `lineEndings` other than `PRESERVE` can read git settings while
configuring, which stops the configuration cache from being reused.

## From Spring's NullAway snippet

[Spring's null-safety guide](https://docs.spring.io/spring-framework/reference/core/null-safety.html)
shows a `JavaCompile` block that turns Error Prone down to NullAway alone, and
the `io.spring.nullability` plugin does the same job. Both go away:

```kotlin
// Remove: the plugin, or this block and its dependencies
plugins { id("io.spring.nullability") version "<version>" }

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableAllChecks = true
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:CustomContractAnnotations", "org.springframework.lang.Contract")
        option("NullAway:JSpecifyMode", "true")
        error("NullAway")
    }
}
dependencies {
    errorprone("com.uber.nullaway:nullaway:<version>")
    errorprone("com.google.errorprone:error_prone_core:<version>")
}
```

and `id("dev.balakmran.sterling")` takes their place. What Sterling sets
beside what the snippet and the plugin do:

| | Spring's snippet or plugin | Sterling |
|---|---|---|
| NullAway at error level, `OnlyNullMarked` | yes | yes, every profile |
| `CustomContractAnnotations` | `org.springframework.lang.Contract` | Not needed: NullAway honours any annotation named `Contract` |
| `CheckContracts` | plugin only | yes |
| `HandleTestAssertionLibraries` | plugin only, for tests | yes, on every compilation |
| JSpecify mode | on | `STRICT` only, or `nullAway { jspecifyMode = true }`. Spring calls it still under development, so Sterling takes it as a second step |
| All of Error Prone's other checks | off (`disableAllChecks`) | **On**: Sterling runs Error Prone in full. Disable a check you aren't ready for with `errorProne { disable("CheckName") }` |
| `RequireExplicitNullMarking` | plugin turns it on | Off; turn it on with `options.errorprone.error("RequireExplicitNullMarking")` |
| JSpecify annotations on the compile classpath | a dependency of yours | Added to every source set's compile-only configuration |

Expect findings the snippet never reported: Error Prone's other checks, and
the contract and assertion checks above. Start on `LENIENT`
(`-Psterling.profile=lenient`) and follow
[Adopting Sterling](adopting.md#a-path-for-an-existing-codebase). Spring's
list of what NullAway can't see is in
[NullAway with Spring](tools.md#nullaway-with-spring).

## From a hand-written build-logic

A convention plugin in `build-logic` often sets Spotless, SpotBugs and JaCoCo
up already. Sterling applies them itself, so:

- **Spotless:** keeping `com.diffplug.spotless` is fine, at any version.
  Gradle loads one Spotless for the whole build script, the higher of yours
  and Sterling's (8.11.0). Remove `googleJavaFormat()` from your
  `spotless { java { } }` block: Sterling adds it at its pinned version, and a
  second one fails. `formatAnnotations()` only clashes with
  `format.formatAnnotations = true`.
- **SpotBugs:** remove the `com.github.spotbugs` plugin. Your `spotbugs { }`
  settings keep working; a convention plugin that sets them applies
  `com.github.spotbugs-base` instead. Effort and report level set there win
  over Sterling's profile, so prefer `sterling { spotbugs { } }` for those.

A convention plugin like this:

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
Sterling themselves (see
[Multi-module builds](adopting.md#multi-module-builds)):

```kotlin
// build-logic/build.gradle.kts
dependencies {
    implementation("dev.balakmran.sterling:sterling-gradle-plugin:<version>")
}
```

If the convention plugin keeps applying Spotless or SpotBugs, apply Sterling
after it, last in `plugins { }`. Then both clashes fail with a message from
Sterling that names the fix. Applied first, Sterling can't step in: the error
comes from the other plugin, as "Cannot add task 'spotbugsMain'" under
`com.github.spotbugs`, or "Multiple steps with name 'google-java-format'" from
Spotless. The same Spotless error comes from a `spotless { }` block in the
build script itself, which always runs after Sterling. The fix is the same
either way.

After the build compiles, run `./gradlew stlInfo` to see what Sterling
resolved, and compare it with what the old setup enforced, in particular the
coverage thresholds: move them to `coverage.instruction` and
`coverage.branch`, and set them to where the codebase is today.
