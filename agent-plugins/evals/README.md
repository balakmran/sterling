# Skill evals

Not installed by any agent. A real agent runs a skill on a fixture, and a
script checks the result: git history, files and Gradle task outcomes, never
the agent's prose. Run by hand before a release; see `docs/plans/agent-plugin.md`
("Skills (evals)") for what must pass.

```bash
agent-plugins/evals/run.sh claude adopt-sterling brownfield-service
agent-plugins/evals/run.sh claude adopt-sterling brownfield-service --style aosp
agent-plugins/evals/run.sh claude fix-sterling-findings findings
agent-plugins/evals/run.sh claude upgrade-sterling previous-sterling
agent-plugins/evals/run.sh claude sterling-conventions greenfield
agent-plugins/evals/run.sh claude adopt-sterling unsupported/gradle-8
```

`run.sh` copies a fixture to a temporary directory, adds this repository's
wrapper and a git history of one commit, loads the plugin with
`claude --plugin-dir`, runs the agent headless with a prompt that pre-approves
the plan, and runs `verify/<skill>.sh`. It prints the work directory and keeps
it when a check fails; the transcript is in `transcript.jsonl`.

## Before the release exists

A skill applies its own `sterling-version`, which isn't on the Plugin Portal
until the release. By default `run.sh` therefore simulates it: it copies this
repository, sets the version in `version.txt`, the manifests and every skill
(`--sterling-version`, default 0.2.0), publishes the Gradle plugin to a
throwaway file repository and points each fixture's `settings.gradle.kts`
at it (the `// @sterling-eval-repo` line). `--published` skips all of this
and uses the plugin as it is, from the Plugin Portal.

## Another harness as the agent

`--prepare-only` builds the project and the simulated plugin and stops.
Load `<work>/plugin` as the plugin in any agent, give it `<work>/prompt.txt` in
`<work>/project`, then `run.sh --verify <work>`.

## Fixtures

| Fixture | Used by | Contents |
|---|---|---|
| `brownfield-service` | `adopt-sterling` | Spring Boot 4, Java 25; Lombok, MapStruct and a generated API interface; Spotless with `googleJavaFormat()` 1.x, full `com.github.spotbugs` at `MAX`, a JaCoCo rule, Spring's NullAway snippet; 4-space, unformatted code; coverage about 55% |
| `brownfield-multi` | `adopt-sterling` | Two modules with different coverage, a `build-logic` convention plugin, a version catalog, an existing `.pre-commit-config.yaml` |
| `greenfield` | `adopt-sterling`, `sterling-conventions` | Spring Initializr output for Boot 4 and Java 25. For `sterling-conventions`, `run.sh` applies Sterling at `STRICT` first |
| `unsupported/gradle-8`, `unsupported/toolchain-17` | `adopt-sterling` | A Gradle 8.14 build and a Java 17 toolchain: the skill must stop and change nothing |
| `findings` | `fix-sterling-findings` | Sterling at `STANDARD` with an Error Prone error, a NullAway finding, a SpotBugs finding and coverage under the threshold |
| `previous-sterling` | `upgrade-sterling` | A Spring service already on Sterling 0.1.0, with the `Application*` exclude and a redundant NullAway option |
