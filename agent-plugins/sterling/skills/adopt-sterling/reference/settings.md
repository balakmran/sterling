# Sterling settings in an adoption

Every setting below exists in Sterling's DSL (`sterling { }`). Detection and
`stlInfo --json` tell you whether one applies.

| Setting | When to set it |
|---|---|
| `format.style` | `GOOGLE` is the default and needs no line. `AOSP` when the user chooses it: `import dev.balakmran.sterling.FormatSettings`, then `sterling { format { style = FormatSettings.Style.AOSP } }`. Commit 1, before the reformat |
| `format.ratchetFrom` | Only when the user picks it instead of the reformat commit: `sterling { format { ratchetFrom = "origin/main" } }`. The reference must exist locally; CI needs `fetch-depth: 0`. Say so in the report |
| `errorProne.disable(...)` | Moves the project's own disabled checks; never add one to make a build pass |
| `nullAway.jspecifyMode` | Only to keep a mode the project already had on |
| `spotbugs.effort`, `spotbugs.reportLevel`, `spotbugs.excludeFilter` | Move the project's own SpotBugs settings here (see `duplicate-config.md`) |
| `coverage.instruction`, `coverage.branch` | From the measurement, per module, in that module's build script |
| `coverage.testTasks` | Leave unset: every `Test` task counts, so an `integrationTest` suite is in the measurement. Set it only if the project's old coverage deliberately counted fewer |
| `coverage.excludes` | Moves the project's existing excludes. Don't add one for the Spring Boot main class or for generated sources: Sterling leaves both out |

## What Sterling does without a setting

- **Spring Boot:** with `org.springframework.boot` applied, the application's
  main class is left out of coverage. Remove an exclude for it (`Application*`).
- **Generated sources:** classes compiled from directories under
  `build/generated` and from annotation processors (OpenAPI, MapStruct) are
  left out of coverage and SpotBugs, and not formatted. Remove excludes for
  them.
- **NullAway:** `OnlyNullMarked`, `CheckContracts` and
  `HandleTestAssertionLibraries` are set on every compilation. Remove the
  project's own copies, and `CustomContractAnnotations`, which is redundant:
  NullAway honours any annotation named `Contract`.
- **Lombok:** detected and reported. Nothing to configure; keep Lombok's
  default `@lombok.Generated` annotations on.
- **Tool versions:** a project pin of Error Prone, NullAway, JSpecify, SpotBugs
  or JaCoCo that is not newer than Sterling's is removed; a newer one is kept
  and reported.
