---
name: verify
description: Run Sterling's required checks before calling a change done - the build, the default and Gradle 9.0.0 x Java 25 functional-test cells, and the dogfood service for plugin changes - and summarise what failed. Use after changing plugin code, tests or build scripts, or when asked to verify.
---

# Verify a Sterling change

CLAUDE.md requires two functional-test cells before a change is done: the
default (latest Gradle, Java 21 toolchain) and Gradle 9.0.0 with Java 25.
Gradle API newer than 9.0 only fails in the second.

## Steps

1. Run the two commands **one after the other, not in parallel** (they share
   the build directory), each in the background with a 10-minute timeout:

   ```bash
   ./gradlew build functionalTest --warning-mode=fail
   ```

   ```bash
   ./gradlew functionalTest --warning-mode=fail -Psterling.test.gradle=9.0.0 -Psterling.test.java=25
   ```

   If the first fails at `build` (compile, Javadoc, `validatePlugins`,
   `buildHealth`, formatting), report that and skip the second: fix first.

2. On failure, find the cause rather than quoting the log tail:
   - Failed tests: read
     `sterling-gradle-plugin/build/test-results/functionalTest/*.xml` or
     `.../test/*.xml` for the test name and the assertion message.
   - A functional test that fails on Gradle output usually names an
     unexpected deprecation. Compare it with `Fixture.KNOWN_DEPRECATIONS`;
     only a third-party plugin's deprecation belongs there.
   - A download timeout (a Gradle distribution, Maven Central) is
     infrastructure: rerun that command once before treating it as a failure.

3. If the change touches plugin behaviour (anything under
   `sterling-gradle-plugin/src/main`), run the dogfood step from CLAUDE.md
   ("Dogfooding after a plugin change") in `../blade-svc-java-webmvc`, with
   `--include-build ../sterling`. Skip it when the earlier commands failed.

4. Report in a short table: each command, pass or fail, and per failure the
   test (class and method), the cause in one line, and the file to look at.
   Say plainly when everything passed; don't hedge.

CI runs only 9.0.0 × 25 for now, so latest × 25 and 9.0.0 × 21 run nowhere.
Mention that.
