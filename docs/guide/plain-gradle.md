# Sterling in plain Gradle

Sterling is configuration, not machinery: each tool it runs is the tool's own
Gradle plugin, set up the way the build scripts below do. If you outgrow
Sterling, or want to see what it does, this is what to copy. Anything you
configure the same way in your own build wins over Sterling's, so you can also
keep Sterling and override one piece.

What Sterling adds beyond these scripts is small: the `stl*` tasks that tie them
together, leaving generated sources out of every tool, the console summaries
and the problems report, `stlFix`, `stlInfo`, and the profile that picks the
defaults. The settings below are `STANDARD`'s.

## Formatting

```kotlin
plugins { id("com.diffplug.spotless") version "8.12.0" }

spotless {
    enforceCheck = false                       // only your gate runs it, not check
    lineEndings = com.diffplug.spotless.LineEnding.PRESERVE
    java {
        googleJavaFormat("2.0.0")              // .aosp() for 4-space indentation
        targetExclude("**/build/generated/**")
        // ratchetFrom("origin/main")
    }
}
```

`stlFmt` is `spotlessApply` and `stlFmtCheck` is `spotlessCheck`.

## Error Prone and NullAway

```kotlin
plugins { id("net.ltgt.errorprone") version "5.2.0" }

dependencies {
    errorprone("com.google.errorprone:error_prone_core:2.53.0")
    errorprone("com.uber.nullaway:nullaway:0.14.4")
    // Every source set, so @NullMarked code in a test suite compiles too.
    compileOnly("org.jspecify:jspecify:1.0.1")
    testCompileOnly("org.jspecify:jspecify:1.0.1")
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*[/\\\\]build[/\\\\]generated[/\\\\].*"
        check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:CheckContracts", "true")
        option("NullAway:HandleTestAssertionLibraries", "true")
        // option("NullAway:JSpecifyMode", "true")   // STRICT
        // allErrorsAsWarnings = true                // LENIENT
    }
    // options.compilerArgs.add("-Werror")           // STRICT
}
```

`stlLint` is `compileJava` and `compileTestJava`. `LENIENT` also sets NullAway to
a warning (an explicit `ERROR` overrides `allErrorsAsWarnings`) and passes
`-Xmaxwarns 10000`, since javac stops printing after 100 warnings.

## SpotBugs

```kotlin
plugins { id("com.github.spotbugs") version "6.5.15" }   // Sterling uses the -base plugin

spotbugs {
    toolVersion = "4.10.5"
    effort = com.github.spotbugs.snom.Effort.DEFAULT        // MAX in STRICT
    reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
    // baselineFile = file("config/spotbugs/baseline.xml")
}

tasks.spotbugsMain {
    excludeFilter = file("config/spotbugs-exclude.xml")   // see below
    reports {
        create("html") { required = true }
        create("xml") { required = true }
        create("sarif") { required = true }
    }
}
// Test classes aren't analysed.
tasks.spotbugsTest { enabled = false }
```

Sterling uses `com.github.spotbugs-base` and registers `spotbugsMain` itself, so
SpotBugs never attaches to `check` on its own; the full plugin does, which is
why the two can't be applied together. Its default exclude filter:

```xml
<FindBugsFilter>
  <!-- Spring constructor injection and records store and return mutable fields. -->
  <Match><Bug pattern="EI_EXPOSE_REP,EI_EXPOSE_REP2"/></Match>
  <!-- Throwing from a constructor only matters for finalizer attacks. -->
  <Match><Bug pattern="CT_CONSTRUCTOR_THROW"/></Match>
</FindBugsFilter>
```

Sterling also adds a `<Match>` for every class compiled from generated sources
(source directories under `build/generated`, and annotation processors'
output). Copy that if you use code generators; `stlGeneratedClasses` writes the
list to `build/sterling/generated-classes.txt`.

## Coverage

```kotlin
plugins { jacoco }

jacoco { toolVersion = "0.8.16" }

tasks.jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit { counter = "INSTRUCTION"; value = "COVEREDRATIO"; minimum = "0.80".toBigDecimal() }
            limit { counter = "BRANCH"; value = "COVEREDRATIO"; minimum = "0.70".toBigDecimal() }
        }
    }
}
```

`stlCov` runs every `Test` task, then the report and the verification.
Sterling counts every test task (an `integrationTest` suite too), leaves out the
generated classes above and, with the Spring Boot plugin, the application's main
class, and attaches the JaCoCo agent only while coverage is on. Thresholds are
80% and 70% in `STANDARD`, 95% and 85% in `STRICT`.

## The gate

```kotlin
tasks.register("stlCheck") {
    dependsOn("spotlessCheck", "compileJava", "compileTestJava", "spotbugsMain",
              "jacocoTestCoverageVerification")
}
tasks.named("check") { dependsOn("stlCheck") }
```

`stlFix` has no plain-Gradle equivalent worth copying: it runs the compilations
with `-XepPatchChecks:...` and `-XepPatchLocation:IN_PLACE`, never cached, then
`spotlessApply`.
