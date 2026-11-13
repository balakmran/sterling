# Contributing to Sterling

Sterling is written in Java; its build scripts use the Gradle Kotlin DSL.
Building it needs JDK 21; its functional tests also need JDK 17, to check the
"needs Java 21" errors, and JDK 25, a consumer toolchain they test. Gradle
finds installed JDKs, including SDKMAN's, and doesn't download any.

```bash
./gradlew build                 # compile, unit tests, Javadoc, plugin validation, dependency checks
./gradlew functionalTest        # tests that run real builds with TestKit
./gradlew spotlessApply         # format Sterling's own code
```

Functional tests run consumer builds on the latest Gradle with a Java 21
toolchain by default. CI runs Gradle 9.0.0 with Java 25; to run that
combination locally (JDK 25 must be installed):

```bash
./gradlew functionalTest -Psterling.test.gradle=9.0.0 -Psterling.test.java=25
```

## Pull requests

`main` takes changes only through pull requests. Branch from it, commit with
[Conventional Commits](https://www.conventionalcommits.org) (`feat:`, `fix:`,
`docs:` and so on), and open a pull request; it's rebase-merged once CI
passes. The commit messages drive the version and the changelog, through
release-please.

A change to the plugin's behaviour needs a functional test, and a new or
changed setting needs its line in the
[settings reference](docs/guide/configuration.md#settings-reference); the
unit tests check that.
