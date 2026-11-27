# Changelog

## [0.3.0](https://github.com/balakmran/sterling/compare/v0.2.0...v0.3.0) (2026-11-27)


### ⚠ Can change findings

* Error Prone 2.53.0 adds checks: `EqualsHashCode` flags a class that overrides `hashCode` without `equals`, `ImpossibleNullComparison` covers `Objects.equals`, `Objects.isNull` and `Objects.nonNull`, and `MustBeClosedChecker` enforces `@MustBeClosed` on method and constructor references.
* NullAway 0.14.4 can report new errors in code that passed before: unboxing a nullable `Iterable`'s elements in an enhanced `for` loop, lambda returns checked against unannotated interface methods, and the implicit receiver of a call inside an anonymous class.
* `stlFmtCheck` fails until you run `stlFmt` once: `format.formatAnnotations` is now off, so Sterling formats as google-java-format does and no longer joins annotations onto one line. Commit the result alone and add it to `.git-blame-ignore-revs`, or set `format.formatAnnotations = true` to keep the old layout.
* Console lines changed (`consoleFormat` 1): coverage lines name the source file (`src/main/java/com/example/Foo.java`), and SpotBugs lines are relative to the root project. A script that parses them needs the new shape.

See [Upgrading Sterling](docs/guide/upgrading.md#020-to-030).

### Features

* **Brownfield adoption** ([#45](https://github.com/balakmran/sterling/issues/45)) ([9ca1d48](https://github.com/balakmran/sterling/commit/9ca1d48fef1e339dada00c61ad2571cb3058f219)):
  * `LENIENT` no longer fails compilation on Error Prone or NullAway errors: they print as warnings (`errorProne.errorsAsWarnings`, on by default in `LENIENT`) and javac prints up to 10,000 of them instead of 100. Setting it together with `warningsAsErrors` fails the build.
  * `format.formatAnnotations` (default off) controls whether Spotless joins annotations onto one line. A project's own `formatAnnotations()` step no longer clashes with Sterling's while it is off.
  * JSpecify's annotations are on the compile classpath of every source set, not only `main` and `test`.
  * `spotbugs.baselineFile` takes a baseline, so only new SpotBugs findings fail the build. Findings match by instance hash, not line number, so renaming a class or method brings its findings back.
* **`stlFix`** ([#48](https://github.com/balakmran/sterling/issues/48)) ([734514b](https://github.com/balakmran/sterling/commit/734514bdcf98bfbfc39225113a46275fe5e1a806)): applies Error Prone's suggested fixes, then formats. `errorProne.patchChecks` chooses the checks; the default is `AnnotationPosition`, `EmptyBlockTag`, `MissingOverride`, `OperatorPrecedence`, `PatternMatchingInstanceof` and `UnnecessaryParentheses`. It never runs in the same build as a verification task, is never up to date or cached, and patches even under `-Werror`.
* **Reporting** ([#49](https://github.com/balakmran/sterling/issues/49)) ([7940a51](https://github.com/balakmran/sterling/commit/7940a51c3f7e50219664f904a1c1a96fa04e85cd)):
  * Console format 1: `<path>[:<line>] [<Tool>:<Rule>] <message>`, relative to the root project. `stlInfo --json` reports `consoleFormat`.
  * SpotBugs findings and coverage shortfalls also appear in Gradle's problems report (Gradle 9.x's incubating Problems API). Builds on a Gradle without it are unaffected.
  * A GitHub Actions problem matcher turns the console lines into pull request annotations; see `docs/guide/ci.md`.
  * `stlInfo` shows how many main packages are `@NullMarked`.
* **Tool versions** ([#50](https://github.com/balakmran/sterling/issues/50)) ([f748311](https://github.com/balakmran/sterling/commit/f748311317bd01dba8aed5503d66da9c4ba90c8f)): Error Prone 2.52.0 to 2.53.0 and NullAway 0.14.3 to 0.14.4. In Sterling's own build: Spotless 8.12.0, the SpotBugs Gradle plugin 6.5.15 and Gradle 9.9.2. google-java-format, JSpecify, SpotBugs and JaCoCo are unchanged.

### Bug Fixes

* **skills:** `outcomes.sh` now accepts `stlFix`, so the agent plugin's skills can run it, and the evals' fixtures and checks exercise it ([#52](https://github.com/balakmran/sterling/issues/52)) ([0dbbc4b](https://github.com/balakmran/sterling/commit/0dbbc4b9f3fbd69e9a8192888100811c25470c04)).

### Documentation

* New pages: Sterling in CI, Sterling in plain Gradle, Compatibility and IDE setup, plus a security policy ([#51](https://github.com/balakmran/sterling/issues/51)). The upgrade guide has a 0.2.0 to 0.3.0 section, and the agent plugin's skills cover `stlFix`, baselines and the reformat decision.

## [0.2.0](https://github.com/balakmran/sterling/compare/v0.1.0...v0.2.0) (2026-11-15)


### ⚠ Can change findings

* NullAway now checks `@Contract` bodies (`CheckContracts`) and treats AssertJ and JUnit assertions as null checks (`HandleTestAssertionLibraries`), so compilation can report new NullAway errors.
* Coverage now counts every `Test` task and leaves out generated classes and, with Spring Boot, the main class, so measured coverage changes. See [Upgrading Sterling](docs/guide/upgrading.md#010-to-020).

### Features

* add format and version sources to stlInfo --json ([de4ee48](https://github.com/balakmran/sterling/commit/de4ee48b7eb9cc62cbf453fba4bfa95cc92ff5ec))
* add stlInfo --json ([3be9b5a](https://github.com/balakmran/sterling/commit/3be9b5a3ae4933b64f5fa669d99671c92dec5e0a))
* agent plugin skills and evals ([#34](https://github.com/balakmran/sterling/issues/34)) ([b300ae5](https://github.com/balakmran/sterling/commit/b300ae541e029d18fc322ae197efae9941be9f85))
* check NullAway contracts and test assertions ([26a9163](https://github.com/balakmran/sterling/commit/26a9163c42e767a5bdb77a863bfaf49ae97e4546))
* coverage counts every test task, skips generated and Boot main classes, and failures print ([#30](https://github.com/balakmran/sterling/issues/30)) ([c9e2126](https://github.com/balakmran/sterling/commit/c9e21261a87decee1b8d697152cfa7a8ed530e61))
* format.ratchetFrom ([faf3334](https://github.com/balakmran/sterling/commit/faf3334ff45106370162d3b4c13479ff9fbdfccc))
* format.style ([b8b815b](https://github.com/balakmran/sterling/commit/b8b815b7e2f27b1509794cdeb48f4be39bea5440))
* show tool version overrides ([806b560](https://github.com/balakmran/sterling/commit/806b560c490cfee0fbc5ee7c48b27f6a35f1b3dd))
* **skills:** detection, task outcome and coverage scripts ([f2aa0f9](https://github.com/balakmran/sterling/commit/f2aa0f9170fde07170d0f9488402a2c7c5c5f695))

## [0.1.0](https://github.com/balakmran/sterling/compare/v0.1.0-alpha.6...v0.1.0) (2026-11-14)


### Bug Fixes

* list spotbugsMain with the verification tasks, not in the sterling group ([07e9e13](https://github.com/balakmran/sterling/commit/07e9e13ab7a3158eb2c7879d41d8e8f1077dfa45))
* show the Spotless and JaCoCo versions in stlInfo ([c1bcc13](https://github.com/balakmran/sterling/commit/c1bcc13c7c73bc9296ad604b1ec8fd8bd7c9de76))

## [0.1.0-alpha.6](https://github.com/balakmran/sterling/compare/v0.1.0-alpha.5...v0.1.0-alpha.6) (2026-11-13)


### Features

* fail with a named fix when a project also sets up SpotBugs or Sterling's Spotless steps ([9d69591](https://github.com/balakmran/sterling/commit/9d69591c038ecd4d3a656f1a9799a91ac027b44b))

## [0.1.0-alpha.5](https://github.com/balakmran/sterling/compare/v0.1.0-alpha.4...v0.1.0-alpha.5) (2026-11-13)


### Features

* add SpotBugs effort and report level, with profile defaults ([e698c89](https://github.com/balakmran/sterling/commit/e698c89a2814daeeddea5a08908bef66211ed015))
* turn on NullAway's JSpecify mode only in STRICT ([4fb71f7](https://github.com/balakmran/sterling/commit/4fb71f7168089b2dcef4af9778c947cfedc587fe))

## [0.1.0-alpha.4](https://github.com/balakmran/sterling/compare/v0.1.0-alpha.3...v0.1.0-alpha.4) (2026-11-12)


### Features

* ship one plugin ID, dev.balakmran.sterling ([182021e](https://github.com/balakmran/sterling/commit/182021ee0504a2c3aa9e1141ef7cb4c59afc6675))

## [0.1.0-alpha.3](https://github.com/balakmran/sterling/compare/v0.1.0-alpha.2...v0.1.0-alpha.3) (2026-11-11)


### Bug Fixes

* apply coverage settings after build scripts run, and allow disabling NullAway while it's off ([29f4644](https://github.com/balakmran/sterling/commit/29f4644b560a1ab19d79dedd6f2b5f8c293b6983))

## [0.1.0-alpha.2](https://github.com/balakmran/sterling/compare/v0.1.0-alpha.1...v0.1.0-alpha.2) (2026-11-10)


### Bug Fixes

* declare configuration-cache support to the Plugin Portal ([ca1860c](https://github.com/balakmran/sterling/commit/ca1860c8e77ea53026eb4a2f23aa4ef9671bb068))

## 0.1.0-alpha.1 (2026-11-10)


### Features

* add the base plugin with the sterling extension and stl tasks ([9725f6f](https://github.com/balakmran/sterling/commit/9725f6f20d6fa13ba02675c720e73f70809fc28e))
* add the Java plugin with the JDK 21 daemon and toolchain checks ([18458b2](https://github.com/balakmran/sterling/commit/18458b28c494ab2984ad99b9778362458d6e87cd))
* analyse bytecode with SpotBugs behind spotbugs.enabled ([b0845ce](https://github.com/balakmran/sterling/commit/b0845ce7398fac52e35175bc9cf25030d5739353))
* check @NullMarked code with NullAway behind nullAway.enabled ([ed24745](https://github.com/balakmran/sterling/commit/ed24745f5cc70e000edfcd84dbdb37d57fdb1712))
* format Java sources with google-java-format via Spotless ([acf3f85](https://github.com/balakmran/sterling/commit/acf3f8536ab7015f2e3dfdd3f0666ad2477ba92f))
* run Error Prone in compilation behind errorProne.enabled ([76f0423](https://github.com/balakmran/sterling/commit/76f0423ee9e9d523eb9a61cafcd53cb44573814d))
* test across Gradle 9.0–9.9 and Java 21–25, and skip generated classes in SpotBugs ([1d048e3](https://github.com/balakmran/sterling/commit/1d048e37864504355fa22e45f89847fbfba19357))
* verify test coverage with JaCoCo behind coverage.enabled ([4c0601a](https://github.com/balakmran/sterling/commit/4c0601a0e051f724fb64c27a023fc6f2db9f2661))
* wire check to stlCheck through attachToCheck ([4c8e17a](https://github.com/balakmran/sterling/commit/4c8e17ac4554fcfe4d56cb83341c3b517069680a))
