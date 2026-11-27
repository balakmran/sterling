# Sterling roadmap

A living document: what Sterling is for, the rules every release follows, and
the order of the next milestones. Each milestone gets its own detailed plan in
[`docs/plans/`](docs/plans/) when work on it starts; the plan, not this page,
is the spec.

## What Sterling is

A tested, version-locked bundle of the modern Java quality stack for Gradle:
google-java-format, Error Prone, NullAway with JSpecify, SpotBugs and JaCoCo.
One plugin, one profile, tool versions known to work together, and a fast
compile-time gate that people and coding agents run locally.

**Who it's for:** Gradle projects on Java 21+, with Spring Boot 4 services as
the primary audience. Spring Framework 7 and Boot 4 adopted JSpecify and check
themselves with NullAway, and Spring now recommends the same for applications.
Spring Boot 3.x is not a target: its open-source support ended in June 2026
and most 3.x services run on Java 17, below Sterling's floor.

**Not for:** Maven builds, Android projects, modules with Kotlin or Groovy
sources, Java 17 toolchains.

**What it replaces:** a hand-written `build-logic`, or the NullAway snippet
copied from Spring's demo project. Spring's own `io.spring.nullability` plugin
sets up Error Prone and NullAway only, so null checking alone isn't the reason
to pick Sterling: the tested bundle and the single gate are. Sterling sits
beside SonarQube, as the local gate that runs before the pull request.

## Rules every milestone follows

1. **Depth before breadth.** Each tool Sterling adds is another version to bump
   and test every month. Making the current tools easy to adopt comes before
   adding new ones.
2. **Lower the adoption wall.** Most teams give up on quality tooling at the
   first wall of findings or the reformat diff. Each milestone should make that
   wall lower.
3. **Wrap, don't reinvent.** Where a healthy plugin or upstream feature exists
   (Spotless ratchet, Error Prone patching, OpenRewrite, CycloneDX,
   dependency-analysis), Sterling exposes it or documents it instead of
   building its own.
4. **New checks start opt-in.** A new tool or rule set is off in `STANDARD`
   until dogfooding shows it's quiet enough to turn on.
5. **The gate stays deterministic.** Nothing in `stlCheck` depends on the
   network, the clock or state the build writes back to the repository. A git
   ref the project names (`ratchetFrom`, the new-code base) is allowed.
6. **One plugin ID per kind of target.** Framework support (Spring, later
   others) reacts to that framework's plugin inside the existing plugin; a new
   ID is added only when its behaviour would be wrong elsewhere.
7. **A bump that can change findings is a minor release.** A new Error Prone
   adds checks and a new google-java-format reformats code, so either can fail
   a build that changed nothing. Those go out as a minor release with a note
   on what changed; only bumps that change no findings are patches.
8. **Every release passes the full matrix.** CI runs one cell on every pull
   request, to save Actions time; the other three run locally with
   `functionalTestLocal` before a change is done and before a release PR
   merges, so no release merges itself. A bump that lands on consumers'
   buildscript classpath waits out a cooldown before it merges.

## Milestones

### v0.1.0 — First stable release

Ship the five tools behind one profile, resolvable from the Plugin Portal.
Scope and acceptance criteria: [`docs/plans/v0.1.0.md`](docs/plans/v0.1.0.md).

Changes agreed before GA:

- **One plugin ID.** `dev.balakmran.sterling` sets up the tools whenever the
  `java` plugin is applied, so there is no separate plugin to choose.
- **`JSpecifyMode` on in `STRICT` only,** behind a new `nullAway.jspecifyMode`
  setting. Spring calls the mode "still under development" and recommends it
  as a second step; it's where most false positives on generic and lambda code
  come from.
- **Dogfood checks:** a Lombok module (`@Data`, `@Builder`) compiles cleanly
  under Error Prone and NullAway and doesn't drag coverage down.
- **Existing tool plugins:** a functional test and a docs note for a project
  that already applies Spotless or SpotBugs at another plugin version, the
  first thing a brownfield build hits.
- **Release policy** for tool bumps (rule 7), stated in the docs.

### v0.2.0 — Adopt it on a real Spring Boot 4 service

Remove the reasons a Spring team backs out in the first hour.

- Spring-aware NullAway defaults: `CheckContracts` and
  `HandleTestAssertionLibraries` on every compilation, and guidance on
  NullAway's known limits with Spring. `CustomContractAnnotations` is dropped:
  NullAway honours any annotation named `Contract`.
- Spring Boot behaviour, such as leaving the application class out of
  coverage, switched on by reacting to `org.springframework.boot`.
- Lombok: a functional test and a README section.
- `format.style`: `GOOGLE` (default) or `AOSP`, the same engine with 4-space
  indentation, so existing code needs a far smaller reformat.
- `format.ratchetFrom`, exposing Spotless's ratchet for brownfield projects.
  It reads git through JGit, so it must pass the configuration cache tests.
- Coverage: leave out classes compiled from generated sources; count extra
  `Test` tasks and JVM test suites such as `integrationTest`.
- Supported overrides of tool versions, so a project can take an Error Prone
  or NullAway release before Sterling ships one.
- When `stlSec` or `stlCov` fails, print the findings or the shortfall to the
  console, so neither a person nor a coding agent has to open an HTML report.
- Multi-module recipe: a convention plugin in `build-logic`, documented and
  covered by a functional test. Aggregation stays in 0.5.0.
- An example Spring Boot 4 service in a public repository. It is the "done
  when" below, and the public proof that closed-source adopters can't give.
- Migration page: from Spring's NullAway snippet, and from a hand-written
  `build-logic`.
- Releases: a Dependabot bump that passes the matrix and changes no findings
  becomes a patch release with no manual steps (rule 7 covers the rest).
- An agent plugin for GitHub Copilot (CLI and VS Code), Claude Code and
  Codex, installed at user scope from the `balakmran/agent-plugins`
  marketplace: skills to adopt Sterling, fix its findings, upgrade it and
  write code that passes it, plus `stlInfo --json` for them to read. Its
  version is Sterling's. Plan:
  [`docs/plans/agent-plugin.md`](docs/plans/agent-plugin.md).

**Done when** a Spring Boot 4 service with Lombok, generated OpenAPI code and an
integration test suite adopts Sterling with nothing in its build but the
plugin, a profile and a coverage exclude or two.

Planned alongside 0.2.0 and not built then; each is now in a milestone's
"done when":

- 0.3.0: `SECURITY.md`, the "what Sterling does in plain Gradle" page (so
  leaving looks cheap), a compatibility table per release, a local run on the
  latest JDK and the next Gradle before each release, and an IDE setup page.
- 0.4.0: a Windows cell and the OpenSSF Scorecard badge.
- 0.5.0: the documentation site (Zensical on GitHub Pages).

### v0.3.0 — A green build on day one, and one-command fixes

Scope and acceptance criteria: [`docs/plans/v0.3.0.md`](docs/plans/v0.3.0.md).

- `LENIENT` reports Error Prone's error-level findings as warnings, so adding
  Sterling never fails a build that compiled before.
- SpotBugs's own baseline support, exposed for brownfield projects.
- Formatting is google-java-format alone, as the Google Java Style Guide
  defines it. Sterling stops adding Spotless's `formatAnnotations()` by
  default: the step picks type-use annotations from a list of simple names,
  not from `@Target` as the guide does (§4.8.5.1), and joins stacked
  annotations such as `@NotBlank @Size(...)` past the 100-column limit
  (§4.4), which `stlFmtCheck` then accepts. `format.formatAnnotations = true`
  brings the step back. Placing type-use annotations right before the type is
  left to Error Prone's `AnnotationPosition`, which reads `@Target`: opt-in
  (rule 4), and a patch `stlFix` can apply. It reformats code 0.2.0 joined,
  so the upgrade notes say to run `stlFmt` once.
- `stlFix`: a separate compile pass with Error Prone's patching, then `stlFmt`.
  It patches a named list of checks, runs outside `stlCheck` and the build
  cache, and a second run changes nothing.
- JSpecify on every source set NullAway checks, not only main and test.
- The lines Sterling prints share one format, `file:line [Tool:Rule] message`,
  versioned like the `stlInfo` JSON. javac's own lines are documented, not
  rewritten.
- A documented GitHub Actions recipe: pull request annotations first, SARIF
  upload where code scanning is available.
- SpotBugs findings and coverage shortfalls reported through Gradle's
  Problems API, which already carries javac's. The API is incubating, and
  Gradle 9.9 deprecated some calls Sterling uses; a change in it can't fail a
  build, because the reporter is created inside a guarded `try`.
- `stlInfo` reports null-safety progress: how many main packages are
  `@NullMarked`.

**Done when** no release merges itself without the full matrix (rule 8),
`SECURITY.md` and the "Sterling in plain Gradle" page exist, and the release
checklist includes a local run on the latest JDK and the next Gradle.

### v0.4.0 — New-code gates

- New-code coverage: the threshold applies to lines changed since a base ref.
  This replaces coverage ratcheting, which would need CI to commit state back.
  The plan names the existing plugins evaluated for it, and the tests cover a
  shallow clone, a missing ref, renamed files and a change with no executable
  lines.

### v0.5.0 — Multi-module builds

- Coverage aggregated across modules (`jacoco-report-aggregation`).
- A settings plugin, only if a real multi-module build can't share Sterling's
  version and settings through a convention plugin in `build-logic`. Whatever
  ships must work with Isolated Projects.
- A build-cache relocation test and published overhead numbers.

### v1.0.0 — Stable

From 1.0 these change only in a major release: the plugin ID, the
`sterling { }` DSL, the `stl*` task names, the `sterling.profile` property,
the `stlInfo --json` schema, the console line format, and the Gradle and Java
floors. A setting is deprecated with a warning for at least one minor before
it is removed. Findings are not part of the promise: a minor release may
raise a tool and fail a build that changed nothing (rule 7). Fixes go to the
latest minor only.

- Declare Isolated Projects support once Gradle stabilises it.
- More examples beside the 0.2.0 one: a multi-module service and a brownfield
  before-and-after.

**Done when** the DSL has gone two minor releases and three months without a
breaking change, and two projects the maintainer doesn't own have upgraded
across a minor using only the upgrade notes.

### After 1.0 — Curated extra rules, opt-in

- Checkstyle for what google-java-format and Error Prone leave: naming, banned
  imports (non-JSpecify nullness annotations, `sun.*`), and Javadoc on public
  API for libraries. Off in `STANDARD` until it's proven quiet.
- `sterling-rules`, published to Maven Central, arrives with Checkstyle if IDE
  sharing is wanted; otherwise the config ships as a plugin resource.
- Spotless `misc` (trailing whitespace, final newline).
- Migration guides from `ru.vyarus.quality` and Palantir Baseline, when
  someone asks for one.

## Decisions log

| Date | Decision | Why |
|---|---|---|
| 2026-11-11 | One plugin ID, `dev.balakmran.sterling`, from 0.1.0 | A second, tool-less ID would add nothing: the tools already wait for `java` |
| 2026-11-11 | `JSpecifyMode` on in `STRICT` only | Spring's guidance; most NullAway false positives come from it |
| 2026-11-11 | `format.style` offers `GOOGLE` and `AOSP` | Lowers the reformat cost for 4-space codebases without a new pin |
| 2026-11-11 | Checkstyle moves from 0.2 to 0.5, opt-in | Small gap over google-java-format and Error Prone; adoption work comes first |
| 2026-11-11 | Spring support lives in the main plugin | Everything planned for Spring is harmless elsewhere or keyed off the Boot plugin |
| 2026-11-11 | FindSecBugs dropped; `stlSec` keeps its name | Slow releases, CodeQL covers it; renaming isn't worth the churn |
| 2026-11-11 | Tool bumps that can change findings are minor releases | A new Error Prone check or a formatter change fails builds that changed nothing |
| 2026-11-11 | Console summary for SpotBugs and coverage moves from 0.3 to 0.2 | "Open the HTML report" is a first-hour reason to back out, and agents can't read it |
| 2026-11-11 | The Spring Boot 4 example moves from 1.0 to 0.2 | Target users are closed-source services; without it there is no public proof |
| 2026-11-11 | Spotless `misc` and `kotlinGradle` move from 0.2 to 0.5 | Breadth, and `kotlinGradle` adds a formatter pin; adoption work comes first |
| 2026-11-11 | No Maven support | A Maven plugin is a second codebase with its own lifecycle; one maintainer can't keep both tested every month |
| 2026-11-15 | `CustomContractAnnotations` dropped from 0.2.0 | NullAway accepts any annotation named `Contract` since 0.12.11, so setting it is redundant; a functional test proves Spring's is honoured |
| 2026-11-15 | An agent plugin ships with 0.2.0, at the same version | Adopting Sterling on a real service is the milestone's goal; skills do the reformat, config removal and measured thresholds that teams back out of by hand |
| 2026-11-15 | The agent plugin is installed at user scope from a separate marketplace repository | Every agent has its own plugin manager; nothing is written into consumer projects |
| 2026-11-26 | Rule 8: every release passes the full matrix, one cell in CI and three locally; consumer-classpath bumps wait out a cooldown | The README promises Gradle 9.0 and latest with Java 21 and 25, but patch releases merged themselves after one CI cell. Running all four cells in CI costs Actions time and slows every PR, so the other three stay local |
| 2026-11-26 | Dependency-only release PRs no longer merge themselves | Reverses 0.2.0's "patch releases with no manual step": nobody runs `functionalTestLocal` in an automated path. Dependabot's own PRs still auto-merge; the full matrix runs once, before the release |
| 2026-11-26 | Rule 5 allows a git ref the project names | `ratchetFrom` already reads one, and new-code coverage needs one |
| 2026-11-26 | `LENIENT` demotes Error Prone errors to warnings, in 0.3.0 | Error-level findings failed compilation on every profile, so adding Sterling could fail the first commit; `LENIENT` exists to avoid that |
| 2026-11-26 | SpotBugs baseline moves from 0.4 to 0.3 | It exposes an existing SpotBugs setting and lowers the adoption wall more than the 0.3 presentation work |
| 2026-11-26 | `formatAnnotations()` off by default from 0.3.0; `format.formatAnnotations` turns it on; type-use placement through Error Prone's `AnnotationPosition`, opt-in | google-java-format is the Google style guide's formatter and never breaks its column limit. Spotless's step guesses type-use annotations by simple name, and its list includes Jakarta's constraint names, so stacked `@NotBlank @Size` were joined into 150-column lines the gate accepted. `AnnotationPosition` identifies type-use annotations by `@Target`, as the guide does. Trimming the name list or re-wrapping after the step was rejected: both keep the name-based guess. A minor release, since it reformats code (rule 7) |
| 2026-11-26 | The Problems API goes into 0.3.0 for SpotBugs and coverage only, guarded so it can't fail a build | Still incubating in 9.9.1, but its reporting interfaces are unchanged since 9.0.0, and Gradle already reports javac's findings through it |
| 2026-11-26 | `AddNullMarkedToPackageInfo` dropped from `stlFix` | It reports only existing `package-info.java` files, not missing ones, and its patch didn't apply on the example service; `stlInfo` lists the packages instead |
| 2026-11-26 | One console format covers Sterling's own lines; javac's are not rewritten | Agents, IDEs and CI matchers already parse javac's format |
| 2026-11-26 | Aggregated SARIF dropped | GitHub accepts one upload per module, each with its own category |
| 2026-11-26 | 0.4.0 split: new-code coverage alone; multi-module aggregation in 0.5.0 | New-code coverage is the largest item on the roadmap, and five features in one milestone would slip |
| 2026-11-26 | Checkstyle, `sterling-rules` and Spotless `misc` move after 1.0 | All are opt-in and additive (rule 4), so they don't need to come before the DSL freeze |
| 2026-11-26 | PMD 7, Picnic's Error Prone Support and the `kotlinGradle` format dropped | Each adds a pin to bump every month for a small gap (rule 1); revisit when a user asks |
| 2026-11-26 | 1.0 defines what is stable and drops "five public projects" | Adopters are mostly closed-source, and findings can't be part of the promise under rule 7 |
| 2026-11-26 | Android and Kotlin source modules named as not supported | Sterling activates on Kotlin JVM modules and does nothing on Android without saying so |
| 2026-11-26 | NullAway's severity follows `errorProne.errorsAsWarnings` | Sterling sets `NullAway:ERROR` explicitly, and an explicit severity is applied after `-XepAllErrorsAsWarnings`, so the blanket demotion alone left NullAway failing compilation on `LENIENT` (found in step 3) |
| 2026-11-26 | `stlFix` patches `AnnotationPosition`, `EmptyBlockTag`, `MissingOverride`, `OperatorPrecedence`, `PatternMatchingInstanceof` and `UnnecessaryParentheses` | Each was shown to patch in a functional test on Error Prone 2.52.0 and again on 2.53.0, and keeps behaviour. Checks that remove or reshape code (`UnusedVariable`, `UnnecessaryLambda`, `ClassCanBeStatic`) are out. `stlFix` adds `-nowarn`, because `-Werror` stops Error Prone writing patches |
| 2026-11-26 | Printed paths are relative to the root project, not the module | A CI matcher, an IDE or an agent opens a file from the repository root; in a single project nothing changes |
| 2026-11-26 | The Problems API reporter is built from a separate class, in a `try` that catches `LinkageError` and `ObjectInstantiationException` | Gradle 9.9 deprecated `ProblemGroup.create`, `ProblemId.create` and `severity`, and from 9.7 every reported problem shows as a warning, so the details carry SpotBugs's rank. A task class naming a missing API type fails at decoration, which can't be caught |
| 2026-11-26 | IntelliJ's google-java-format plugin 2.0.0.x is the one to install | It bundles the formatter Sterling pins, and it can't be pointed at another version, so an older plugin disagrees with `stlFmtCheck` |
