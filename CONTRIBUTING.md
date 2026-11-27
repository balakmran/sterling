# Contributing to Sterling

Sterling is written in Java; its build scripts use the Gradle Kotlin DSL.
Building it needs JDK 21; its functional tests also need JDK 17, to check the
"needs Java 21" errors, and JDK 25, a consumer toolchain they test. Gradle
finds installed JDKs, including SDKMAN's, and doesn't download any.

```bash
./gradlew build                 # compile, unit tests, Javadoc, plugin validation, dependency checks
./gradlew functionalTestLocal   # tests that run real builds with TestKit
./gradlew spotlessApply         # format Sterling's own code
```

Functional tests run consumer builds on Gradle 9.0.0 and the latest Gradle,
with Java 21 and 25 toolchains. CI runs Gradle 9.0.0 with Java 25, and
`functionalTestLocal` runs the other three combinations; run it before
opening a pull request. To run one combination, CI's for example:

```bash
./gradlew functionalTest -Psterling.test.gradle=9.0.0 -Psterling.test.java=25
```

## Pull requests

`main` takes changes only through pull requests. Branch from it, commit with
[Conventional Commits](https://www.conventionalcommits.org) (`feat:`, `fix:`,
`docs:` and so on), and open a pull request; it's rebase-merged once CI
passes. The commit messages drive the version and the changelog, through
release-please.

Dependabot bumps that can't change findings merge themselves once CI passes.
No release does: CI runs one test cell, so a release PR waits for the
[release checklist](#releasing). A bump of Error Prone, NullAway, SpotBugs or google-java-format is labelled
`changes-findings` instead: reword its commit to `feat(deps):` and say what
may newly fail, because it can fail builds that changed nothing.

A change to the plugin's behaviour needs a functional test, and a new or
changed setting needs its line in the
[settings reference](docs/guide/configuration.md#settings-reference); the
unit tests check that.

## Releasing

release-please opens a release PR after changes land on `main`, whatever they
are, and nothing merges it but the maintainer. CI ran one test cell on every
change, so before merging:

1. Check out the release PR's branch and run `./gradlew build
   functionalTestLocal`, which runs the other three cells.
2. Run the latest JDK and the next Gradle once: `./gradlew functionalTest
   -Psterling.test.java=27`, then `-Psterling.test.gradle=<newest release
   candidate or nightly>`. A failure is reported in the release PR, and blocks
   the release only if it's a regression in a supported cell.
3. Dogfood `../blade-svc-java-webmvc` as `CLAUDE.md` describes, and run the
   agent plugin's evals if the release changes a skill.
4. Merge the release PR. The `publish` job uploads to the Plugin Portal and
   attests the jars' build provenance; then merge the marketplace PR it opens.
