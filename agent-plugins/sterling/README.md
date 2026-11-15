# Sterling agent plugin

Skills that help a coding agent adopt and maintain
[Sterling](https://github.com/balakmran/sterling), the quality gate for Java
21+ Gradle builds: google-java-format, Error Prone, NullAway with JSpecify,
SpotBugs and JaCoCo, behind one profile and one task, `stlCheck`.

The plugin works with GitHub Copilot (CLI and VS Code), Claude Code and Codex.
Install it once, for your user, from the `balakmran` marketplace; nothing is
added to the projects you work on. See Sterling's README for the install
command for each agent.

## Install

```bash
claude plugin marketplace add balakmran/agent-plugins --scope user
claude plugin install sterling@balakmran --scope user
```

Other agents are in Sterling's README, under "Using Sterling with a coding
agent".

## Skills

| Skill | Use it to |
|---|---|
| `adopt-sterling` | Add Sterling to a new or existing Gradle project as a short series of reviewable commits: the plugin, one reformat commit hidden from `git blame`, the old tool config removed, coverage thresholds set from the measured value, and a pre-commit hook. Stops when Gradle is below 9.0 or the JDK is below 21. |
| `fix-sterling-findings` | Fix what `stlCheck` reports, in rounds: Error Prone and NullAway errors, SpotBugs findings, coverage below the threshold, then formatting. Suppresses only as a last resort, with a reason. |
| `upgrade-sterling` | Move a project to the Sterling version this plugin was released with, following the upgrade notes, with a separate reformat commit only when google-java-format changed. |
| `sterling-conventions` | Write Java that passes the gate the first time. The agent uses it whenever it edits Java in a project that applies Sterling. |

Each skill carries the Sterling version it was released with, in
`metadata.sterling-version`, and the plugin's version is always Sterling's.
The skills never push, never open pull requests and never lower a threshold;
each ends with a report of what it ran and committed.
