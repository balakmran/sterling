# The tools

## Pinned tool versions

Sterling configures these versions of each tool:

| Tool | Version |
|---|---|
| google-java-format | 2.0.0 |
| Spotless | 8.11.0 |
| Error Prone | 2.52.0 |
| NullAway | 0.14.3 |
| JSpecify | 1.0.1 |
| SpotBugs | 4.10.5 |
| JaCoCo | 0.8.16 |

Spotless is the Gradle plugin that runs google-java-format. It's on the
build script classpath rather than downloaded, so a build that also asks
for a newer Spotless gets that one: Gradle picks the higher version.

## Using a different tool version

Every tool except google-java-format can be moved to another version than
Sterling pins, newer or older. This is supported: Sterling then leaves its
own version out, and `stlInfo` marks the tool's version
`(project override)`.

| Tool | How |
|---|---|
| Error Prone | `errorprone("com.google.errorprone:error_prone_core:<version>")` in `dependencies { }` |
| NullAway | `errorprone("com.uber.nullaway:nullaway:<version>")` |
| JSpecify | `compileOnly("org.jspecify:jspecify:<version>")` |
| SpotBugs | `spotbugs { toolVersion = "<version>" }` |
| JaCoCo | `jacoco { toolVersion = "<version>" }` |

A dependency declared without a version, as from a platform, shows as
`managed (project override)`. google-java-format can't be overridden: it's
the one tool whose upgrade rewrites code, and its pin is what makes
`stlFmtCheck` agree on every machine.

Remove an override once Sterling pins that version or a later one; the
release notes of a Sterling release that raises a tool say so.

## What an upgrade can change

Sterling's version number says whether upgrading it can fail a build that
changed nothing:

- **Minor release** (0.1.x to 0.2.0): may raise Error Prone, NullAway,
  SpotBugs or google-java-format. A new Error Prone or NullAway can add
  checks, a new SpotBugs can find more, and a new google-java-format can
  reformat code. The changelog says what may newly fail. Before 1.0, a minor
  release may also change a setting or a profile default.
- **Patch release** (0.1.0 to 0.1.1): fixes, and bumps that can't change
  findings, such as JSpecify or JaCoCo.

Pin Sterling's version, and take minor releases on purpose: in their own
pull request, with `stlFmt` run and any new findings fixed or disabled. Each
release's changes are in [Upgrading Sterling](upgrading.md).

## Formatting

`stlFmt` and `stlFmtCheck` cover every source set. Sources under any
`build/generated` directory (OpenAPI generators, annotation processors,
Spring AOT) are left alone. Each file keeps the line endings it has; enforce
those with `.gitattributes` or Spotless's own `lineEndings` setting.

Sterling formats as google-java-format does, which is the Google Java Style
Guide's layout. For annotations that means declaration annotations one per
line before the declaration, and a lone field annotation on the field's line
(§4.8.5). Spotless's `formatAnnotations()` step is off: it picks type-use
annotations by name rather than by `@Target`, and joined stacked Bean
Validation annotations onto lines past the 100-column limit. Set
`format.formatAnnotations = true` to turn it back on; files formatted without
it then fail `stlFmtCheck` until `stlFmt` runs.

`format.style = FormatSettings.Style.AOSP` runs the same google-java-format
with 4-space indentation instead of 2, for a codebase that already indents
by 4 (see [Reformatting](adopting.md#reformatting-and-first-findings)).

## Error Prone

Error Prone runs inside every Java compilation. Error-level findings fail it
unless `errorsAsWarnings` is on, as it is in `LENIENT`, where they print as
warnings and javac's cap of 100 printed warnings is raised to 10,000.
Warnings fail it only with `warningsAsErrors`; the two settings together are
rejected. Sources under
`build/generated` are skipped entirely, and warnings in code annotated
`@Generated` are ignored.

## Null checking with NullAway

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

`nullAway.jspecifyMode` also checks nullness in generic type arguments
(`List<@Nullable String>`), arrays and varargs. It's on only in `STRICT`:
Spring calls the mode still under development and recommends turning it on as
a second step, once a codebase is clean without it, because most false
positives on generic and lambda-heavy code come from it.

Sterling adds [JSpecify](https://jspecify.dev) to `compileOnly` and
`testCompileOnly` while NullAway is on, so the annotations work without a
dependency of your own. Spring Boot 4 apps get it through `spring-core` too.
A **library** whose public API uses these annotations should still declare
`api("org.jspecify:jspecify:1.0.1")`, so Kotlin callers and other tools see
them.

## NullAway with Spring

While NullAway is on, Sterling passes it these options on every Java
compilation:

| Option | Value | What it does |
|---|---|---|
| `NullAway:OnlyNullMarked` | `true` | Checks only `@NullMarked` code |
| `NullAway:CheckContracts` | `true` | Checks that a method's body keeps its `@Contract` when the contract ends in `!null`, such as `!null -> !null`; NullAway doesn't verify `-> fail` |
| `NullAway:HandleTestAssertionLibraries` | `true` | Treats AssertJ's and JUnit's assertions, such as `assertThat(x).isNotNull()`, as null checks |
| `NullAway:JSpecifyMode` | `true` when `nullAway.jspecifyMode` is on | See [above](#null-checking-with-nullaway) |

Spring's `CustomContractAnnotations=org.springframework.lang.Contract` isn't
needed: NullAway honours any annotation named `Contract`, so after
`Assert.notNull(x, "...")` it knows `x` isn't null, and AssertJ's own
contracts work the same way. `CheckContracts` can report findings in code
that compiled before; it reports only in `@NullMarked` code.

To change one of these, set it on the compilation. A project's own option
wins over Sterling's:

```kotlin
import net.ltgt.gradle.errorprone.errorprone

tasks.withType<JavaCompile>().configureEach {
    options.errorprone.option("NullAway:CheckContracts", "false")
}
```

JSpecify mode needs javac to keep type annotations on compiled classes: JDK
22 or later, or JDK 21.0.8+ with `-XDaddTypeAnnotationsToSymbol=true`, which
the Error Prone Gradle plugin adds. Some vendors' JDK 21 builds don't
support the flag; use a JDK 22+ toolchain for `STRICT` if yours doesn't.

NullAway can't follow every Spring pattern. Spring's
[null-safety guide](https://docs.spring.io/spring-framework/reference/core/null-safety.html)
lists the known limits and how it suppresses each:

| Case | Suppression |
|---|---|
| A field set after construction, as in `InitializingBean.afterPropertiesSet()` | `@SuppressWarnings("NullAway.Init")` on the field, constructor or class |
| A path that can't happen, which NullAway's dataflow can't see | `@SuppressWarnings("NullAway") // Dataflow analysis limitation` |
| A null check outside a lambda, used inside it | `@SuppressWarnings("NullAway") // Lambda` |
| Reflection known to return non-null | `@SuppressWarnings("NullAway") // Reflection` |
| `Map.get` with a key known to be present | `@SuppressWarnings("NullAway") // Well-known map keys` |
| Overriding a method whose superclass doesn't declare nullness | `@SuppressWarnings("NullAway") // Overridden method does not define nullability`; nullness annotations aren't inherited, so repeat them on overrides |
| Nullness of a generic method's type variable | `@SuppressWarnings("NullAway")`, see [NullAway#1075](https://github.com/uber/NullAway/issues/1075) |

Arrays need the annotation in the right place: `@Nullable Object[]` allows
null elements, `Object @Nullable []` a null array, and both together allow
both. NullAway checks array elements only in JSpecify mode. For a nested or
qualified type, the annotation goes after the last dot:
`Cache.@Nullable ValueWrapper`.

## SpotBugs

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

## Coverage

Coverage is measured on every `Test` task in the project: `test`, and any
JVM test suite such as `integrationTest`. `stlCov` runs them all, then
`jacocoTestReport` writes HTML and XML reports from their combined execution
data and `stlCov` checks them against the thresholds. To count only some of
them, name them in `coverage.testTasks`; the others run without the JaCoCo
agent. With coverage off, no test task gets the agent at all.

Classes compiled from generated sources are always left out of the report
and the verification: the same classes SpotBugs skips, from source
directories under `build/generated` (OpenAPI generators and the like) and
annotation processors' output (MapStruct and others), each with its nested
classes. `stlGeneratedClasses` lists them in
`build/sterling/generated-classes.txt`. There's no setting for this: JaCoCo
can't filter by source path, and the `@Generated` those generators add
(`javax` or `jakarta`) is kept only in source, where JaCoCo can't see it.

With the `org.springframework.boot` plugin applied, the application's main
class is left out too, with its nested classes: the class Spring Boot's
`resolveMainClassName` finds, or the one `springBoot { mainClass }` names.
Tests rarely run its `main` method, and every Boot service used to need an
exclude for it.

JaCoCo itself ignores classes and methods annotated with any annotation whose
name contains `Generated` and that's kept in the class file, such as one of
your own. It's Java's equivalent of `# pragma: no cover`. For generated code
that lives somewhere else, use `coverage.excludes`.

**Lombok** 1.18.34 and later mark the code they write with
`@lombok.Generated`, so getters, `equals`, builders and the rest don't count
against coverage. Error Prone skips its warnings in that code, and NullAway
relies on the annotation to leave Lombok's code unchecked, so don't turn it
off with `lombok.addLombokGeneratedAnnotation = false`. On an older Lombok,
turn it on in a `lombok.config` at the project root:

```properties
config.stopBubbling = true
lombok.addLombokGeneratedAnnotation = true
```

NullAway's Lombok support is best effort: `@Builder`, `@Data`, `@Value` and
`@Builder.Default` on a `@Nullable` field work in `@NullMarked` code.

`coverage.excludes` patterns match class files under the classes directory,
such as `com/example/Legacy*`. End them with `*`: nested and anonymous
classes compile to their own files, like `Legacy$1.class`, which
`com/example/Legacy.class` alone would miss.

## Where the reports are

| Tool | Output |
|---|---|
| google-java-format | `stlFmtCheck` lists the unformatted files; `stlFmt` fixes them |
| Error Prone and NullAway | The compiler output of `compileJava` and `compileTestJava`, with no report file |
| SpotBugs | `build/reports/spotbugs/main.html`, `main.xml` and `main.sarif` |
| JaCoCo | `build/reports/jacoco/test/html/index.html` and `jacocoTestReport.xml` |

When `stlSec` or `stlCov` fails, the console says why, so neither report has
to be opened first:

- **SpotBugs** (`stlSecSummary`, after `spotbugsMain`): one line per finding,
  `src/main/java/com/example/Foo.java:42 [SpotBugs:NP_NULL_ON_SOME_PATH] message`,
  at most 50, then `... and N more:` with the HTML report's path.
- **JaCoCo** (`stlCovSummary`, after a failed verification): the measured
  instruction and branch coverage against the thresholds, then the ten source
  files with the most missed instructions, such as
  `com/example/Foo.java [JaCoCo:INSTRUCTION] 12 of 40 missed`. It prints
  nothing when the verification passes.

Neither changes the outcome: `spotbugsMain` and the verification fail as
they always did.

## Suppressing one finding

Fix the code when you can. When a finding is wrong, or right but not worth
the change, suppress that one finding rather than turning a check off:

| Tool | How |
|---|---|
| Error Prone | `@SuppressWarnings("CheckName")` on the method, field or class, with the check name from the finding |
| NullAway | `@SuppressWarnings("NullAway")`, or `@Nullable` where null really is allowed |
| SpotBugs | `@SuppressFBWarnings(value = "PATTERN", justification = "...")`, from `compileOnly("com.github.spotbugs:spotbugs-annotations:4.10.5")`; or a `<Match>` in `spotbugs.excludeFilter` |
| Coverage | An annotation of your own whose name contains `Generated`, kept in the class file (`@Retention(CLASS)`); or `coverage.excludes` for whole classes |

To turn a check off everywhere, see
[Settings](configuration.md#settings): `errorProne { disable("CheckName") }`,
or `spotbugs.excludeFilter` for a SpotBugs pattern.
