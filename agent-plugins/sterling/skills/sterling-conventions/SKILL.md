---
name: sterling-conventions
description: >-
  Write Java that passes the Sterling quality gate (dev.balakmran.sterling)
  the first time: google-java-format, Error Prone, NullAway with JSpecify,
  SpotBugs and JaCoCo coverage. Use when writing or editing Java in a Gradle
  project whose build applies the dev.balakmran.sterling plugin.
license: Apache-2.0
metadata:
  sterling-version: "0.1.0" # x-release-please-version
---

# Writing Java that passes Sterling

Sterling runs five tools behind one task, `stlCheck`. These rules are the ones
that gate can fail on. Follow them while writing code, then run the checks
below before you say the work is done.

## First, check that Sterling applies

Look for `id("dev.balakmran.sterling")` in the build scripts, a `build-logic`
convention plugin, or `settings.gradle.kts`. If it isn't there, this skill
doesn't apply; stop following it.

Find the active profile: `./gradlew -q stlInfo` prints it. `STRICT` adds the
rules marked "(STRICT)" below.

## After every change to Java code

Run, from the project root:

```bash
./gradlew stlFmt stlLint -q
```

`stlFmt` rewrites files with google-java-format, so never format by hand and
never argue with its output. `stlLint` compiles with Error Prone and NullAway;
fix what it reports before moving on. Never edit files under `build/generated`:
they are regenerated, and Sterling skips them.

Before you finish, run the whole gate and read the task outcomes, not the log:

```bash
./gradlew stlCheck
```

## Null safety (NullAway with JSpecify)

NullAway checks only code in packages marked `@NullMarked` in
`package-info.java`. Inside them:

- Annotate anything that may be null with `org.jspecify.annotations.Nullable`,
  never `javax.annotation`, `jakarta.annotation`, `org.springframework.lang` or
  JetBrains annotations. Put it on the type: `@Nullable String name`,
  `String @Nullable [] values`.
- Check a `@Nullable` value before dereferencing it, or pass it only where
  `@Nullable` is accepted.
- Assign every non-null field in every constructor. A field that is set later
  is `@Nullable`.
- When you create a new package in a codebase whose packages are already
  `@NullMarked`, add a `package-info.java` with `@NullMarked` to it too.
- Never remove `@NullMarked` from a package, or add `@NullUnmarked`, to make an
  error go away.
- (STRICT) JSpecify mode also checks generics, arrays and varargs:
  `List<@Nullable String>` for a list that may hold nulls.

## Dependency injection

Use constructor injection with `final` fields, not field or setter injection.
Final fields assigned in the constructor satisfy NullAway's initialization
check, and Sterling's SpotBugs filter already accepts the stored references
they create.

## Error Prone

Error-level findings fail compilation in every profile. (STRICT) Warnings fail
it too, including javac's own `[removal]` and similar warnings. Fix the code
rather than the build: Error Prone's message names the check and links its
explanation.

## SpotBugs and coverage

Outside `LENIENT`, `stlSec` runs SpotBugs on the main classes and `stlCov`
fails when tests cover less than the thresholds `stlInfo` shows. New code
ships with tests that keep the module above them.

## Suppressing a finding

Only when a finding is wrong, and always with a comment giving the reason on
the line above:

```java
// NullAway can't see that init() runs before any use; see FooLifecycle.
@SuppressWarnings("NullAway.Init")
```

Never lower a coverage threshold, disable a check in `sterling { }`, or add a
coverage exclude to make the gate pass. Those are decisions for the project's
maintainers; say what you'd change and why instead.
