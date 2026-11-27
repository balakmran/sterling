# IDE setup

Sterling formats with google-java-format 2.0.0, and `stlFmtCheck` is the
judge: if your editor formats a file differently, the gate fails. This page
keeps the two in agreement, and keeps Error Prone and NullAway running when
you build from the IDE.

## IntelliJ IDEA

**Formatting.** Install the
[google-java-format plugin](https://plugins.jetbrains.com/plugin/8527-google-java-format)
at **2.0.0.x**. Its first three numbers are the google-java-format version it
bundles, and Sterling's is 2.0.0 (see [the tools](tools.md#pinned-tool-versions)).
The plugin can't be pointed at another formatter version, so a 1.x plugin
reformats code that `stlFmtCheck` then rejects: google-java-format 2.0 changed
its layout engine. 2.0.0.x needs IntelliJ 2025.1 or later; on an older IDE, or
with a plugin whose version doesn't match, turn the plugin off and run
`./gradlew stlFmt`.

1. Add the six `--add-exports` lines from
   [the plugin's README](https://github.com/google/google-java-format#intellij-android-studio-and-other-jetbrains-ides)
   under **Help > Edit Custom VM Options**, and restart. The plugin only shows
   a notification when they're missing; it doesn't add them.
2. Under **Settings > Tools > google-java-format Settings**, enable it and set
   **Code style** to match `sterling { format { style } }`: `GOOGLE` unless you
   chose `AOSP`. Leave **Max line length** at 100. Settings are per project.
3. Reformat Code and Optimize Imports now use it, including **Actions on Save**.

A whole-file Reformat Code also applies google-java-format's clean-ups (redundant
semicolons, unused imports, modifier order). IntelliJ's partial reformats, such
as formatting only the lines you changed, skip them, so `stlFmt` can still find
something to change. Run it before you commit.

**Error Prone and NullAway.** They run as options on Gradle's `JavaCompile`
tasks, so build with Gradle: under **Settings > Build, Execution, Deployment >
Build Tools > Gradle**, set **Build and run using** to **Gradle** (the default).
IntelliJ's own compiler doesn't run Sterling's setup, and NullAway's findings
appear when Gradle compiles, not as editor inspections. `./gradlew stlLint`
shows them without running the tests. The separate Error-prone Compiler plugin
isn't a substitute: it knows nothing of Sterling's NullAway options or pinned
versions.

## VS Code

Format on save with the version Sterling pins by letting Gradle do it, either
way:

- **Spotless Gradle** (`richardwillis.vscode-spotless-gradle`) runs
  `spotlessApply` on the open file through Spotless's IDE hook, so it uses
  Sterling's formatter version and style. Set, for Java:

  ```json
  {
    "[java]": {
      "spotlessGradle.format.enable": true,
      "editor.defaultFormatter": "richardwillis.vscode-spotless-gradle"
    },
    "java.format.enabled": false
  }
  ```

- **A task.** Run `./gradlew stlFmt` from the terminal or as a task, before you
  commit. It works with any editor.

The google-java-format extensions track the latest release by default; if you
use one, pin its version setting to `2.0.0` (and pass `--aosp` for the `AOSP`
style), or it will disagree with `stlFmtCheck`.

## Other editors and agents

Anything that can run a command can run `./gradlew stlFmt stlLint -q`, which
formats and compiles with Error Prone and NullAway, and exits non-zero with the
findings on the console, in [a format tools can read](tools.md#reading-sterlings-output).
A git hook that runs it is in [Adopting Sterling](adopting.md).

## When Sterling bumps the formatter

A Sterling release that moves google-java-format says so in
[Upgrading Sterling](upgrading.md), and reformatting is one `stlFmt` commit.
Update the IntelliJ plugin to the matching version at the same time.
