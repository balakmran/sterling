# Sterling

Opinionated, configurable code-quality enforcement for Gradle Java projects.
Published as a Gradle plugin under `dev.balakmran.sterling`.

Current milestone: **v0.1.0**. The plan, scope, configuration model and
acceptance criteria are in `docs/plans/v0.1.0.md`. Read it before starting work
and do not build anything listed under "Out" there. Later milestones are in
`ROADMAP.md`.

## Identifiers

| Thing | Value |
|---|---|
| Maven group | `dev.balakmran.sterling` |
| Plugin artifact | `sterling-gradle-plugin` |
| Plugin ID | `dev.balakmran.sterling` |
| Java package | `dev.balakmran.sterling` |
| Extension name | `sterling` |
| Task group | `sterling` |
| Task prefix | `stl` (e.g. `stlFmt`, `stlCheck`) |

These are frozen. Do not rename them.

## What v0.1.0 ships

Five tools, each with an `enabled` flag whose default comes from `profile`:

| Tool | Gradle plugin applied | Sterling task |
|---|---|---|
| google-java-format | `com.diffplug.spotless` | `stlFmt`, `stlFmtCheck` |
| Error Prone | `net.ltgt.errorprone` | `stlLint` |
| NullAway | none; an Error Prone plugin in the `errorprone` configuration, plus JSpecify on `compileOnly` | `stlLint` |
| SpotBugs | `com.github.spotbugs-base` | `stlSec` |
| JaCoCo | `jacoco` (core) | `stlCov` |

`stlCheck` aggregates the enabled verify tasks; `stlInfo` prints the resolved
configuration.

## Layout

```
sterling/
├── ROADMAP.md                       # milestones after the current one
├── settings.gradle.kts
├── gradle/libs.versions.toml        # every version lives here
├── sterling-gradle-plugin/
│   └── src/
│       ├── main/java/dev/balakmran/sterling/  # public API and the plugin
│       ├── main/java/.../sterling/internal/   # task types; not for consumers
│       ├── main/resources/          # SpotBugs exclude filter (versions.properties is generated)
│       ├── test/java/...            # unit tests (ProjectBuilder)
│       └── functionalTest/java/...  # TestKit tests (the important ones)
├── docs/plans/
└── .github/workflows/
```

`sterling-rules` (Checkstyle config) arrives with Checkstyle, planned for
v0.5.0 in `ROADMAP.md`. Do not create it yet.

## Commands

```bash
./gradlew build                          # compile, unit tests, validatePlugins, buildHealth
./gradlew functionalTest                 # TestKit tests: Gradle x toolchain matrix
./gradlew spotlessApply                  # format this repo's Java and build scripts
./gradlew publishToMavenLocal            # for dogfooding in a consumer project
./gradlew publishPlugins --validate-only # publishing dry run
```

Run `./gradlew build functionalTest` before declaring any task done.

## Plugin authoring rules

These are non-negotiable. Most Gradle plugin bugs come from breaking one of them.

1. **Configuration cache safe.** Never capture `Project`, `Task` or the extension
   inside a task action or a lambda that runs at execution time. Task inputs are
   `Property`/`Provider` values wired at configuration time.
2. **Lazy everything.** `tasks.register` and `tasks.named`, never `tasks.create`
   or `tasks.getByName`. No `afterEvaluate`.
3. **Conventions, not values.** Extension defaults use `.convention(...)` so a
   consumer's explicit `set(...)` always wins. Profile-derived defaults use
   `profile.map { ... }`.
4. **Flags never decide plugin application.** The consumer's `sterling { }`
   block runs after Sterling is applied. Always apply the underlying plugins;
   honour each `enabled` flag lazily: `dependsOn`/`finalizedBy` through
   `Flags.whenOn` (never `flag.map`, whose tasks Gradle can drop), `onlyIf` on a
   `Provider<Boolean>`, `errorprone.isEnabled` bound to a provider, plain (non-`Property`)
   tool values such as the JaCoCo agent's `enabled` set in a `taskGraph.whenReady`
   listener (a configuration action can run before `sterling { }`), and
   tool dependencies added inside `withDependencies { }` so nothing resolves
   when a tool is off.
5. **React, don't apply.** The plugin reacts with
   `pluginManager.withPlugin("java") { ... }`. It never applies `java` itself.
6. **Wrap tool tasks, don't replace them.** `stl*` tasks are aggregates that
   `dependsOn` the underlying tool tasks. The tools' own task names keep working.
   Exception: use `com.github.spotbugs-base`, not `com.github.spotbugs`, and
   register `spotbugsMain` in Sterling so SpotBugs never attaches itself to `check`.
7. **Sterling owns `check`.** Tools must not attach themselves to `check`
   (Spotless `enforceCheck = false`, SpotBugs base plugin). Only `stlCheck` is
   wired to `check`, and only when `attachToCheck` (default from the profile)
   says so.
8. **Change or verify, never both.** A task either modifies sources (`stlFmt`)
   or verifies them. Never one task that does both.
9. **Every task has `group = "sterling"` and a one-line `description`.**
10. **Strict validation passes.** `validatePlugins` runs with
    `enableStricterValidation = true`; annotate every task property.

## Compatibility targets

- **Consumers:** Java 21+ project toolchain, Gradle 9.0+, and the Gradle
  daemon on JDK 21+ (google-java-format runs inside it). Sterling fails fast
  with a clear message on a toolchain below 21 (`stlToolchainCheck`) and, at
  apply time, on a daemon JVM below 21. Java 17 is not supported.
- **Plugin bytecode:** JVM 17, so Sterling still loads on a JDK 17 daemon and
  can print that message instead of an `UnsupportedClassVersionError`. Don't
  raise it, and don't add Java 17 support on the strength of it.
  Build Sterling itself with a JDK 21 toolchain.
- **Languages:** the plugin and its tests are Java, compiled with
  `--release 17`, so Java 17 language features only. Build scripts are Gradle
  Kotlin DSL. No Kotlin source code.
- Functional tests run against Gradle 9.0.0 and the latest stable Gradle, with
  consumer toolchains 21 and 25. `-Psterling.test.gradle=9.0.0` and
  `-Psterling.test.java=25` pick a cell (default: latest × 21); CI runs all four.
  Before declaring a change done, run at least the default cell and the
  9.0.0 × 25 one; Gradle API newer than 9.0 only fails there.

When pinning a version (Gradle, Spotless, google-java-format, Error Prone,
NullAway, JSpecify, SpotBugs, JaCoCo), check the current stable release rather
than relying on memory or the plan, and record it in `gradle/libs.versions.toml`.
If a version in the plan doesn't exist, use the latest available and update the plan.

## Testing

- Functional tests build throwaway projects in a JUnit `@TempDir` and run them
  with `GradleRunner`. They are the source of truth for behaviour.
- Every case in the plan's test table has a functional test.
- Every `enabled` flag has a test that turns it off and asserts the tool did
  not run (and, for dependency-backed tools, that its artifact wasn't resolved).
- Deprecations: functional tests run with `--warning-mode=all` and fail on any
  Gradle deprecation except those in `Fixture.KNOWN_DEPRECATIONS`, each a
  third-party plugin's, to be removed when that plugin fixes it.
- Configuration cache: tests run with `--configuration-cache` and
  `--configuration-cache-problems=fail`, and at least one test asserts the cache
  is reused on a second run.
- Assert on task outcomes (`TaskOutcome.SUCCESS`, `FAILED`, `UP_TO_DATE`,
  `SKIPPED`), not just on log text.

## Conventions

- Conventional Commits (`feat:`, `fix:`, `test:`, `build:`, `docs:`, `ci:`).
  release-please derives versions and the changelog from them.
- One logical change per commit, following the work breakdown in the plan.
- Trunk-based: never commit to `main`. Branch from it as `feat/`, `fix/`,
  `ci/`, `docs/`, `build/` or `test/` plus a short name, open a PR, and
  rebase-merge once CI passes, so commit messages reach `main` unchanged. CI
  runs on PRs only. release-please's PRs run no CI (`GITHUB_TOKEN` starts no
  workflows); they're merged with the admin bypass.
- Sterling's own Java is formatted with google-java-format, and build scripts
  with ktfmt, both via Spotless. javac runs with `-Xlint:all -Werror`.
- Code comments (`//` and `/* */` inside method bodies, fields and build
  scripts) are concise and direct: one line, two at most. Say why, not what.
  This limit doesn't apply to Javadoc.
- Javadoc is as long as it needs to be, in Google Java Style: a summary
  fragment, then paragraphs (`<p>`) for defaults, behaviour and examples, then
  `@param`, `@return` and `@throws` as they apply. Every public class, method
  and enum constant has it, and so do package-private helpers worth explaining.
  Each package has a `package-info.java`. The `javadoc` task runs in `build`
  with `-Xdoclint:all -Xwerror`, so missing or broken public docs fail it.
- Code, test names and comments never cite the plan (its test or step numbers).
  The plan is temporary; name what's being tested instead.
- `gradle/libs.versions.toml` follows the rules in its header: grouped, sorted,
  and every alias named after its version key.
- No new dependency without a reason; every plugin dependency ends up on the
  consumer's buildscript classpath. `buildHealth` (dependency-analysis) fails
  the build on unused or misdeclared dependencies.
- Public API is the `dev.balakmran.sterling` package: the plugin, the
  extension, its settings classes and `Profile`, each with Javadoc. Anything
  else is package-private, or public in `dev.balakmran.sterling.internal` when
  Gradle needs it (task types).
