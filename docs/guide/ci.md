# Sterling in CI

Run `./gradlew stlCheck` (or `build`, which runs it through `check`). A failure
says what's wrong on the console; this page turns those lines into annotations
on the pull request.

## Annotations from the console output

[`sterling-problem-matcher.json`](sterling-problem-matcher.json) is a GitHub
Actions [problem matcher](https://github.com/actions/toolkit/blob/main/docs/problem-matchers.md).
It annotates the files and lines in javac's diagnostics (Error Prone and
NullAway) and in Sterling's [console format](tools.md#reading-sterlings-output)
(SpotBugs and JaCoCo). It needs no code scanning, so it works on private
repositories.

Copy it into your repository, then register it before the build:

```yaml
- name: Register Sterling's problem matcher
  run: echo "::add-matcher::.github/sterling-problem-matcher.json"

- name: Check
  run: ./gradlew stlCheck
```

Findings appear as annotations on the changed lines of the pull request.
Sterling's own lines are all errors; javac's are errors or warnings as javac
reports them. Paths are relative to the root project, so the repository root
must be the Gradle root, as it is in a default checkout.

## SARIF for SpotBugs

Where code scanning is available, SpotBugs's SARIF report can go there instead,
which keeps findings in the Security tab. `spotbugsMain` writes it to
`build/reports/spotbugs/main.sarif` (`stlInfo --json` lists the path):

```yaml
- name: Check
  run: ./gradlew stlCheck

- name: Upload SpotBugs findings
  if: always()
  uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: build/reports/spotbugs/main.sarif
```

`if: always()` uploads the report when the check fails, which is when it matters.
Pin the action to a commit, as for any third-party action.
