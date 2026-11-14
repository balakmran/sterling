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
pull request, with `stlFmt` run and any new findings fixed or disabled.

## Formatting

`stlFmt` and `stlFmtCheck` cover every source set. Sources under any
`build/generated` directory (OpenAPI generators, annotation processors,
Spring AOT) are left alone. Each file keeps the line endings it has; enforce
those with `.gitattributes` or Spotless's own `lineEndings` setting.

## Error Prone

Error Prone runs inside every Java compilation. Error-level findings always
fail it; warnings fail it only with `warningsAsErrors`. Sources under
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

Coverage is measured on the `test` task. After it runs, `jacocoTestReport`
writes HTML and XML reports, and `stlCov` checks them against the
thresholds. With coverage off, tests run without the JaCoCo agent at all.

JaCoCo already ignores classes and methods annotated with any annotation whose
name contains `Generated` and that's kept in the class file, such as one of
your own. It's Java's equivalent of `# pragma: no cover`. Annotations kept
only in source, like MapStruct's or `jakarta.annotation.Generated`, aren't
seen; use `coverage.excludes` for those.

**Lombok** marks the code it writes with `@lombok.Generated` only when told
to. Without this `lombok.config` at the project root, every getter, `equals`
and builder counts against coverage:

```properties
config.stopBubbling = true
lombok.addLombokGeneratedAnnotation = true
```

`coverage.excludes` patterns match class files under the classes directory,
such as `com/example/Application*`. End them with `*`: nested and anonymous
classes compile to their own files, like `Application$1.class`, which
`com/example/Application.class` alone would miss.

## Where the reports are

| Tool | Output |
|---|---|
| google-java-format | `stlFmtCheck` lists the unformatted files; `stlFmt` fixes them |
| Error Prone and NullAway | The compiler output of `compileJava` and `compileTestJava`, with no report file |
| SpotBugs | `build/reports/spotbugs/main.html`, `main.xml` and `main.sarif` |
| JaCoCo | `build/reports/jacoco/test/html/index.html` and `jacocoTestReport.xml` |

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
