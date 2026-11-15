# Duplicate configuration

What a build may already set up that Sterling now owns. The first column
matches the "What to remove" table in Sterling's `docs/guide/migrating.md`
(a test keeps them equal); the second says what this skill does. The commit
numbers are those in `SKILL.md`.

Read the project's build scripts, convention plugins and `gradle.properties`
for each row. Spring's NullAway snippet and `io.spring.nullability` are rows
in the same table: remove the plugin or the `tasks.withType<JavaCompile>`
block, and the `errorprone(...)` declarations that went with it.

| In your build | What to do |
|---|---|
| The `com.github.spotbugs` plugin | Remove it (commit 1: Sterling won't configure beside it). Keep its `spotbugs { }` settings for commit 4 |
| `spotbugs { effort }` and `spotbugs { reportLevel }` | Move to `sterling { spotbugs { effort; reportLevel } }`, same values |
| `spotbugs { excludeFilter }` | Move to `sterling { spotbugs { excludeFilter } }`, same file |
| `googleJavaFormat()` in `spotless { java { } }` | Remove (commit 1: a second one fails) |
| `formatAnnotations()` in `spotless { java { } }` | Remove (commit 1: a second one fails) |
| `removeUnusedImports()`, `trimTrailingWhitespace()` and `endWithNewline()` on Java | Remove from the Java block (commit 4) |
| Other Spotless steps, such as a license header | Keep, in the same block |
| `errorprone("com.google.errorprone:error_prone_core:<version>")`, `errorprone("com.uber.nullaway:nullaway:<version>")` and `compileOnly("org.jspecify:jspecify:<version>")` | Remove each declaration, then read the Error Prone, NullAway and JSpecify versions from `stlInfo --json`; if the removed version was newer than Sterling's, put it back and report it as a project override |
| `options.errorprone` settings for `NullAway`, `NullAway:OnlyNullMarked`, `NullAway:JSpecifyMode`, `NullAway:CheckContracts`, `NullAway:HandleTestAssertionLibraries` and `NullAway:CustomContractAnnotations` | Remove each; `nullAway { jspecifyMode = true }` only if the project had JSpecify mode on and the profile isn't `STRICT` |
| `disableAllChecks`, and `disable("CheckName")` on `JavaCompile` | Remove `disableAllChecks`, and say in the report that Error Prone now runs in full; move each `disable("Name")` to `errorProne { disable("Name") }` |
| JaCoCo `violationRules` | Remove; `coverage.instruction` and `coverage.branch` come from the measurement (commit 5), never from the old minimum |
| JaCoCo class excludes | Move each to `coverage.excludes`, unless it names generated sources or the Spring Boot main class, which Sterling skips; drop those |
| `check { dependsOn(...) }` on a JaCoCo or SpotBugs task | Remove it (commit 4); `check` runs `stlCheck` |
| Test tasks wired to `jacocoTestReport` | Remove the wiring; leave `coverage.testTasks` unset unless the project deliberately counted fewer |
| Checkstyle and PMD | Leave alone; list in the report |

## Rules that apply to every row

- A SpotBugs setting moved into `sterling { spotbugs { } }` changes type:
  `import dev.balakmran.sterling.SpotbugsSettings`, then
  `effort = SpotbugsSettings.Effort.MAX` and
  `reportLevel = SpotbugsSettings.Confidence.MEDIUM`, in place of the
  `com.github.spotbugs.snom` enums. `excludeFilter` takes a `File`.
- Remove only what the row names. Leave every other Spotless step, other
  `spotbugs { }` settings, other `options.errorprone` options and other
  `jacoco { }` settings in place.
- `target(...)` and `isEnforceCheck`/`enforceCheck` in a `spotless { }` block
  are not steps: leave them and mention them in the report.
- Checkstyle and PMD stay as they are.
- A tool plugin that's declared in a convention plugin stays there if other
  code in it still needs it (a Spotless license-header step, for example);
  apply Sterling after it, last in `plugins { }`.
- Say in the report what you removed or moved, and where.
