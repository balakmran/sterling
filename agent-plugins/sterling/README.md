# Sterling agent plugin

Skills that help a coding agent adopt and maintain
[Sterling](https://github.com/balakmran/sterling), the quality gate for Java
21+ Gradle builds: google-java-format, Error Prone, NullAway with JSpecify,
SpotBugs and JaCoCo, behind one profile and one task, `stlCheck`.

The plugin works with GitHub Copilot (CLI and VS Code), Claude Code and Codex.
Install it once, for your user, from the `balakmran` marketplace; nothing is
added to the projects you work on. See Sterling's README for the install
command for each agent.

## Skills

| Skill | Use it to |
|---|---|
| `sterling-conventions` | Write Java that passes the gate the first time. The agent uses it whenever it edits Java in a project that applies Sterling. |

More skills arrive with Sterling 0.2.0: adopting Sterling in a new or existing
project, fixing what the gate finds, and upgrading Sterling.

Each skill carries the Sterling version it was released with, in
`metadata.sterling-version`, and the plugin's version is always Sterling's.
