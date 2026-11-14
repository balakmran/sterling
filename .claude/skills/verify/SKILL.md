---
name: verify
description: Run Sterling's required checks before calling a change done - the build, the three functional-test cells CI skips (functionalTestLocal), and the dogfood service for plugin changes - and summarise what failed. Use after changing plugin code, tests or build scripts, or when asked to verify.
---

# Verify a Sterling change

Functional tests cover four cells: Gradle 9.0.0 and the latest, with Java 21
and 25 consumer toolchains. CI runs only 9.0.0 × 25; `functionalTestLocal`
runs the other three, and CLAUDE.md requires them before a change is done.

## Steps

1. Run, in the background with a 10-minute timeout:

   ```bash
   ./gradlew build functionalTestLocal --warning-mode=fail
   ```

   It runs `functionalTestLatestJava21`, `functionalTestLatestJava25` and
   `functionalTestGradle90Java21`. If it fails at `build` (compile, Javadoc,
   `validatePlugins`, `buildHealth`, formatting), report that: fix first.

2. On failure, find the cause rather than quoting the log tail:
   - Failed tests: read
     `sterling-gradle-plugin/build/test-results/<cell task>/*.xml` (or
     `.../test/*.xml`) for the test name and the assertion message, and say
     which cell failed.
   - A functional test that fails on Gradle output usually names an
     unexpected deprecation. Compare it with `Fixture.KNOWN_DEPRECATIONS`;
     only a third-party plugin's deprecation belongs there.
   - A download timeout (a Gradle distribution, Maven Central) is
     infrastructure: rerun once before treating it as a failure.

3. If the change touches plugin behaviour (anything under
   `sterling-gradle-plugin/src/main`), run the dogfood step from CLAUDE.md
   ("Dogfooding after a plugin change") in `../blade-svc-java-webmvc`, with
   `--include-build ../sterling`. Skip it when the earlier commands failed.

4. Report in a short table: each cell (and the build), pass or fail, and per
   failure the test (class and method), the cause in one line, and the file
   to look at. Say plainly when everything passed; don't hedge. The fourth
   cell, 9.0.0 × 25, runs in CI on the pull request.
