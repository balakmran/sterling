---
name: sterling-conventions
description: >-
  Write Java that passes the Sterling quality gate (dev.balakmran.sterling)
  the first time: google-java-format, Error Prone, NullAway with JSpecify,
  SpotBugs and JaCoCo coverage. Use when writing or editing Java in a Gradle
  project whose build applies the dev.balakmran.sterling plugin.
license: Apache-2.0
metadata:
  sterling-version: "0.3.0" # x-release-please-version
---

# Writing Java that passes Sterling

Sterling runs five tools behind one task, `stlCheck`. These rules are the ones
that gate can fail on. Follow them while writing code, then run the checks
below before you say the work is done.

## First, check that Sterling applies

Look for `id("dev.balakmran.sterling")` in the build scripts, a `build-logic`
convention plugin, or `settings.gradle.kts`. If it isn't there, this skill
doesn't apply; stop following it.

Find the active profile and formatter style: `./gradlew -q stlInfo --json`
prints them as `profile` and `format.style`. `STRICT` adds the rules marked
"(STRICT)" below.

## After every change to Java code

Run, from the project root:

```bash
./gradlew stlFmt stlLint -q
```

`stlFmt` rewrites files with google-java-format, so never format by hand and
never argue with its output. `stlLint` compiles with Error Prone and NullAway;
fix what it reports before moving on. Never edit files under `build/generated`:
they are regenerated, and Sterling skips them. `stlFmt` formats every source
file, so on a project that was never formatted it also touches files you didn't
edit; tell the user, and don't mix that diff into your own change.

Before you finish, run the whole gate and read the task outcomes, not the log:

```bash
./gradlew stlCheck
```

## Null safety (NullAway with JSpecify)

NullAway checks only code in packages marked `@NullMarked` in
`package-info.java`. If no package in the project is marked, NullAway checks
nothing yet; don't add `@NullMarked` unasked, but still annotate with `@Nullable`
where null is meant. Inside marked packages:

- Annotate anything that may be null with `org.jspecify.annotations.Nullable`,
  never `javax.annotation`, `jakarta.annotation`, `org.springframework.lang` or
  JetBrains annotations. Put it on the type: `@Nullable String name`,
  `String @Nullable [] values`. A type-use annotation such as `@Nullable`
  goes after the modifiers, directly before the type: `private @Nullable String
  note;`, `public @Nullable String find()`, never `@Nullable private String`.
  Declaration annotations (Jakarta's `@NotBlank` and `@Size`, `@Deprecated`,
  `@Override`) go before the declaration, one per line. `./gradlew stlFix`
  moves misplaced `@Nullable`s; run it on its own, never with `stlCheck`.
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

Compilation runs every default Error Prone check. Error-level findings fail
the build in every profile; (STRICT) warnings fail it too, javac's own
(`[removal]`, `[deprecation]`) included. Each message names the check and the
fix, and `https://errorprone.info/bugpattern/<CheckName>` explains it. The ones
that code written by an agent trips most, first checked against Error Prone
2.52.0 (2.53.0 adds `EqualsHashCode` for `hashCode` without `equals`):

Errors (fail in every profile):

- `ArrayEquals`, `ArrayToString`: `Arrays.equals(a, b)`, `Arrays.toString(a)`,
  never `a.equals(b)` or `"" + a` on arrays
- `EqualsHashCode`: override `hashCode` whenever you override `equals`, and
  `equals` whenever you override `hashCode`
- `ReturnValueIgnored`, `CheckReturnValue`: use the result of `String`,
  `Optional`, stream and `@CheckReturnValue` methods
- `FormatString`: format placeholders match their arguments
- `SelfAssignment`, `SelfEquals`, `ModifyingCollectionWithItself`: no
  `this.x = x` with the same variable on both sides, and no `list.addAll(list)`
- `DoubleBraceInitialization`: no `new ArrayList<>() {{ add(...); }}`; use
  `List.of(...)`
- `LongLiteralLowerCaseSuffix`: `1L`, never `1l`
- `JUnit4TestNotRun`, `UseCorrectAssertInTests`: JUnit tests are runnable
  and assert with the test framework, not `assert`

Warnings ((STRICT) fails on these):

- `UnusedVariable`, `UnusedMethod`, `UnusedNestedClass`: delete what is unused,
  including private fields, parameters of private methods and locals
- `MissingOverride`: `@Override` on every override
- `ReferenceEquality`: `.equals` for objects, `==` only for primitives, enums
  and deliberate identity
- `DefaultCharset`, `StringCaseLocaleUsage`: pass a charset
  (`StandardCharsets.UTF_8`) and a locale (`Locale.ROOT`) explicitly
- `JavaTimeDefaultTimeZone`: `Instant.now(clock)`, `LocalDate.now(zone)`, never
  the no-argument `now()`; inject a `Clock`
- `StringSplitter`: `Splitter` or `split(regex, -1)` with a limit, not `split(regex)`
- `JdkObsolete`: `ArrayDeque` over `Stack`, `StringBuilder` over `StringBuffer`,
  `HashMap` over `Hashtable`, no `LinkedList` as a queue
- `OperatorPrecedence`: parenthesise `a && b || c` and mixed shifts
- `EmptyCatch`, `CatchAndPrintStackTrace`, `SystemOut`: handle or rethrow an
  exception, log through the project's logger, never `printStackTrace()` or
  `System.out`
- `MissingCasesInEnumSwitch`, `FallThrough`: a `switch` covers every constant
  or has a `default`; `break` or comment a deliberate fall-through
- `ClassCanBeStatic`, `MutablePublicArray`, `MixedMutabilityReturnType`,
  `FutureReturnValueIgnored`, `JavaUtilDate`, `InlineMeSuggester`: make a
  nested class that doesn't use its outer instance `static`; keep arrays
  private; return one kind of list (always immutable or always mutable);
  use a returned `Future`; `java.time`, not `Date`
- `ObjectToString`, `UnnecessaryParentheses`, `InconsistentCapitalization`

## SpotBugs and coverage

From `STANDARD`, `stlSec` runs SpotBugs on the main classes (not tests) and
`stlCov` fails when tests cover less than the thresholds `stlInfo` shows. The
SpotBugs patterns code written by an agent trips most, checked against
SpotBugs 4.10.5:

- `DM_DEFAULT_ENCODING`: explicit charsets, as above
- `RV_RETURN_VALUE_IGNORED_BAD_PRACTICE`, `RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT`,
  `RV_RETURN_VALUE_IGNORED`: use the result of `File.delete()`,
  `Lock.tryLock()` and methods of immutable types such as `String` and
  `BigDecimal`; `Files.delete(path)` throws instead
- `MS_MUTABLE_ARRAY`, `MS_MUTABLE_COLLECTION`, `MS_SHOULD_BE_FINAL`,
  `MS_PKGPROTECT`: no public or package-visible mutable `static` fields; keep
  them `private`, or `static final` with `List.of(...)` / `Map.of(...)`
- `NP_NULL_ON_SOME_PATH`, `NP_NONNULL_RETURN_VIOLATION`: check for null before
  you dereference, and don't return null from a method documented not to
- `DLS_DEAD_LOCAL_STORE`, `UUF_UNUSED_FIELD`, `URF_UNREAD_FIELD`: no unused
  locals or fields
- `DM_EXIT`: throw an exception, never `System.exit` outside `main`
- `ES_COMPARING_STRINGS_WITH_EQ`: `.equals` for strings
- `SE_BAD_FIELD`, `SE_NO_SERIALVERSIONID`: `Serializable` classes have a
  `serialVersionUID` and only serializable or `transient` fields
- `OBL_UNSATISFIED_OBLIGATION`: close streams and connections with
  try-with-resources
- `PZLA_PREFER_ZERO_LENGTH_ARRAYS`: return an empty array, not `null`

Sterling's filter already skips `EI_EXPOSE_REP`, `EI_EXPOSE_REP2` and
`CT_CONSTRUCTOR_THROW`, so Spring constructor injection and records need
nothing here. New code ships with tests that keep the module above its
coverage thresholds; read them from `stlInfo --json`. A new class with no
test is the usual way to fall below.

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
