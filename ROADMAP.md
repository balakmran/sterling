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

**Not for:** Maven builds, Kotlin or Groovy sources, Java 17 toolchains.

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
   network, the clock or state the build writes back to the repository.
6. **One plugin ID per kind of target.** Framework support (Spring, later
   others) reacts to that framework's plugin inside the existing plugin; a new
   ID is added only when its behaviour would be wrong elsewhere.
7. **A bump that can change findings is a minor release.** A new Error Prone
   adds checks and a new google-java-format reformats code, so either can fail
   a build that changed nothing. Those go out as a minor release with a note
   on what changed; only bumps that change no findings are patches.

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

- Spring-aware NullAway defaults, starting with
  `CustomContractAnnotations=org.springframework.lang.Contract`, and README
  guidance for NullAway's known limitations with Spring and Reactor APIs.
  Compare with what `io.spring.nullability` sets first, including its test
  options (AssertJ's `Contract`, `HandleTestAssertionLibraries`).
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
  covered by a functional test. Aggregation stays in 0.4.0.
- An example Spring Boot 4 service in a public repository. It is the "done
  when" below, and the public proof that closed-source adopters can't give.
- Migration page: from Spring's NullAway snippet, and from a hand-written
  `build-logic`.
- Releases: a Dependabot bump that passes the matrix and changes no findings
  becomes a patch release with no manual steps (rule 7 covers the rest).

**Done when** a Spring Boot 4 service with Lombok, generated OpenAPI code and an
integration test suite adopts Sterling with nothing in its build but the
plugin, a profile and a coverage exclude or two.

Alongside, not tied to a release (none of it changes the plugin):

- Test matrix: a Groovy DSL project, one Windows cell, and a non-blocking
  latest-JDK cell.
- Documentation site (Zensical on GitHub Pages).
- Trust signals: `SECURITY.md`, an OpenSSF Scorecard badge, a compatibility
  table per release, and a "what Sterling does in plain Gradle" page, so
  leaving looks cheap.

### v0.3.0 — Readable failures and one-command fixes

- One console format for every tool, `file:line [Tool:Rule] message`,
  building on the 0.2.0 summary.
- Report findings through Gradle's Problems API as well. Don't promise IDE
  integration until it's verified.
- A documented GitHub Actions recipe: SARIF upload and pull request
  annotations.
- `stlFix`: a separate compile pass with Error Prone's patching, then `stlFmt`.
  It changes sources, so it is never part of `stlCheck`. Includes
  `AddNullMarkedToPackageInfo` as an opt-in patch for marking packages
  `@NullMarked`.
- `stlInfo` reports null-safety progress: how many main packages are
  `@NullMarked`.

### v0.4.0 — Multi-module builds and new-code gates

- Coverage aggregated across modules (`jacoco-report-aggregation`), and an
  aggregated SARIF report.
- A settings plugin, only if convention plugins in `build-logic` prove
  insufficient. Whatever ships must work with Isolated Projects.
- New-code coverage: the threshold applies to lines changed since a base ref.
  This replaces coverage ratcheting, which would need CI to commit state back.
- SpotBugs's own baseline support, exposed for brownfield projects.

### v0.5.0 — Curated extra rules, opt-in

- Checkstyle for what google-java-format and Error Prone leave: naming, banned
  imports (non-JSpecify nullness annotations, `sun.*`), and Javadoc on public
  API for libraries. Off in `STANDARD` until it's proven quiet.
- `sterling-rules`, published to Maven Central, arrives with Checkstyle if IDE
  sharing is wanted; otherwise the config ships as a plugin resource.
- Evaluate PMD 7 and Picnic's Error Prone Support as `STRICT`-only extras. Each
  goes in only with a dogfood report showing more signal than noise.
- Spotless `misc` (trailing whitespace, final newline) and `kotlinGradle`
  formats.

### v1.0.0 — Stable

- Freeze the DSL. Declare Isolated Projects support once Gradle stabilises it.
- Migration guides from `ru.vyarus.quality` and Palantir Baseline.
- More examples beside the 0.2.0 one: a multi-module service and a brownfield
  before-and-after.

**Done when** the DSL has gone two minor releases without a breaking change
and at least five public projects build with Sterling.

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
