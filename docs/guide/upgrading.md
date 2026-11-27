# Upgrading Sterling

One section per release that changes something you may notice, newest first:
settings renamed or removed, defaults that changed, and tool bumps that can
change findings. Why a minor release can fail a build that changed nothing is
in [What an upgrade can change](tools.md#what-an-upgrade-can-change).

Move one minor release at a time, in its own pull request: bump the version,
run `./gradlew build`, and fix or disable what newly fails.

## 0.2.0 to 0.3.0

**Run `stlFmt` once.** `format.formatAnnotations` is off, so Sterling formats
as google-java-format does, and files where 0.2.0 joined annotations onto one
line (and past the 100-column limit) fail `stlFmtCheck` until reformatted,
though google-java-format is still 2.0.0. Commit the result alone, and add it
to `.git-blame-ignore-revs`. To keep the old layout, set
`format.formatAnnotations = true`. A project's own `formatAnnotations()` in
its `spotless { java { } }` block no longer clashes with Sterling's while that
setting is off.

**Can add findings.** Run the build before merging the upgrade.

- **Error Prone 2.53.0.** `EqualsHashCode` now flags a class that overrides
  `hashCode` without `equals`; `ImpossibleNullComparison` flags impossible null
  comparisons through `Objects.equals`, `Objects.isNull` and `Objects.nonNull`;
  `MustBeClosedChecker` enforces `@MustBeClosed` on method and constructor
  references.
- **NullAway 0.14.4.** Mostly bug fixes, but some report new errors in code that
  passed before: unboxing of a nullable `Iterable`'s elements in an enhanced
  `for` loop, lambda returns checked against unannotated interface methods, and
  the implicit receiver of a call inside an anonymous class.

**`LENIENT` no longer fails compilation** on Error Prone's or NullAway's errors:
they print as warnings (`errorProne.errorsAsWarnings`), and javac prints up to
10,000 of them instead of 100. `STANDARD` and `STRICT` still fail on them. Setting
`errorsAsWarnings` together with `warningsAsErrors` fails the build.

**Console lines changed.** Coverage lines name the source file, such as
`src/main/java/com/example/Foo.java [JaCoCo:INSTRUCTION] 12 of 40 missed`,
where 0.2.0 printed `com/example/Foo.java`. In a multi-module build, SpotBugs
lines are relative to the root project, not the module. A script that parses
these lines should read the new shape; `stlInfo --json` reports it as
`consoleFormat` 1 (see [Reading Sterling's output](tools.md#reading-sterlings-output)).

**Other tool versions:** Spotless 8.12.0, the SpotBugs Gradle plugin 6.5.15 and
Gradle 9.9.2 for Sterling's own build; google-java-format, JSpecify, SpotBugs and
JaCoCo are unchanged.

**New, and optional:**

- [`stlFix`](tools.md#fixing-with-stlfix) applies Error Prone's fixes, such as
  moving `@Nullable` after the modifiers, then formats; `errorProne.patchChecks`
  chooses the checks.
- `spotbugs.baselineFile` takes a [baseline](tools.md#spotbugs-baseline), so only
  new SpotBugs findings fail.
- NullAway's JSpecify annotations are now on every source set's compile classpath,
  not only `main` and `test`.
- `stlInfo` shows how many main packages are `@NullMarked`.
- SpotBugs findings and coverage shortfalls also appear in Gradle's problems
  report, and [a GitHub Actions problem matcher](ci.md) annotates pull requests.
- New pages: [Sterling in CI](ci.md), [Sterling in plain Gradle](plain-gradle.md),
  [Compatibility](compatibility.md) and [IDE setup](ide.md).

## 0.1.0 to 0.2.0

**Can add findings.** Run the build before merging the upgrade.

**New NullAway options.** Sterling now sets `NullAway:CheckContracts` and
`NullAway:HandleTestAssertionLibraries` on every compilation. `CheckContracts`
reports a method whose body breaks its `@Contract` when the contract ends in
`!null`, such as `!null -> !null`, in `@NullMarked` code. The other option
makes AssertJ's and JUnit's assertions count as null checks, so it removes
findings in tests. Remove your own copies of either option, and of
`NullAway:CustomContractAnnotations`, which NullAway doesn't need for any
annotation named `Contract`. To keep one off, set it on the compilation
(see [NullAway with Spring](tools.md#nullaway-with-spring)).

**Coverage numbers change.** `stlCov` now counts every `Test` task, such as an
`integrationTest` suite, and leaves out classes compiled from generated
sources and, with the `org.springframework.boot` plugin, the application's
main class. Measured coverage usually rises, so thresholds you set to the old
number may now be loose: re-measure with `./gradlew stlCov` and raise them. If
you listed the integration tests' tasks or generated packages yourself, they
are now redundant. To go back to counting only `test`, set
`coverage.testTasks = setOf("test")`.

**The `Application*` exclude can go.** 0.1.0's README suggested
`coverage.excludes.add("com/example/Application*")` for a Spring Boot service.
Sterling now leaves the main class out itself; remove the exclude, and any
other that only covered generated code.

**google-java-format is unchanged** (2.0.0), so `stlFmtCheck` gives the same
answer as before and no reformat is needed.

**Other tool versions** are the same as 0.1.0: Error Prone 2.52.0, NullAway
0.14.3, JSpecify 1.0.1, SpotBugs 4.10.5, JaCoCo 0.8.16.

**New, and optional:**

- `format.style = AOSP` formats with 4-space indentation, and
  `format.ratchetFrom` formats only files that differ from a git reference
  (see [Adopting](adopting.md#reformatting-and-first-findings)).
- `coverage.testTasks` narrows which `Test` tasks count.
- Tool versions can be overridden, supported and shown by `stlInfo` as
  "project override" (see [the tools](tools.md#using-a-different-tool-version)).
- `stlSec` and `stlCov` print their findings and shortfall to the console when
  they fail.
- `./gradlew -q stlInfo --json` prints the configuration as JSON
  (see [Configuring Sterling](configuration.md#reading-the-configuration-as-json)).
