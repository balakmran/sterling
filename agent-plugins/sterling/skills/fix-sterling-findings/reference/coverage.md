# Coverage below the threshold

`findings.sh` prints a `total` line per module (instruction and branch
percentages) and up to ten `class` lines with the most missed instructions.
Compare the totals with `coverage.instruction` and `coverage.branch` from
`stlInfo --json`; each module has its own thresholds. Coverage counts every
`Test` task, such as `integrationTest`, and leaves out generated classes and
the Spring Boot main class.

- Write tests for the listed classes, most missed first. Test behaviour:
  public methods, both sides of each branch, the error paths. A test that runs
  code without asserting anything is not a fix.
- Put tests beside the existing ones, in the same style and framework.
- Re-measure with `scripts/outcomes.sh stlCov` after each batch; stop when it
  succeeds. Don't write more than needed.
- Never lower `coverage.instruction` or `coverage.branch`, never add a
  `coverage.excludes` entry, never annotate code `@Generated`, and never delete
  code to raise the percentage. If a class can't be tested without a design
  change, report it.
- When a branch percentage can't be reached because the module has no
  branches, say so; that is a settings decision.
