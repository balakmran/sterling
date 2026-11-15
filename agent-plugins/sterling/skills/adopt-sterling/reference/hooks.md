# The pre-commit hook

The hook runs `./gradlew stlFmtCheck -q` before each commit. It's committed as
configuration so the whole team gets it. Use the project's own hook manager;
a second one would fight it over `core.hooksPath`. Read `git.hookManager` from
detection.

| `hookManager` | What to do |
|---|---|
| `pre-commit` (prek or pre-commit, a `.pre-commit-config.yaml` exists) | Add the hook below to that file: under an existing `repo: local`, else as a new `repo: local` entry |
| `none`, or `git-hook` (an untracked `.git/hooks/pre-commit`) | Create `.pre-commit-config.yaml` with the hook below |
| `husky` | Append `./gradlew stlFmtCheck -q` to `.husky/pre-commit` |
| `lefthook` | Add a command to the `pre-commit` section of `lefthook.yml` |
| `core.hooksPath` | Append the command to the `pre-commit` script in that directory if it's tracked in the repository; otherwise create `.pre-commit-config.yaml` and say in the report that the hooks path needs a decision |

```yaml
repos:
  - repo: local
    hooks:
      - id: stl-fmt-check
        name: Check formatting
        entry: ./gradlew stlFmtCheck -q
        language: system
        pass_filenames: false
        stages: [pre-commit]
```

Lefthook:

```yaml
pre-commit:
  commands:
    stl-fmt-check:
      run: ./gradlew stlFmtCheck -q
```

Never run `prek install`, `pre-commit install` or write under `.git/hooks`.
The report asks the user, and the team, to run `prek install` (or the
project's own install step) once per clone.

Running the whole gate before a push is optional; Sterling's
`docs/guide/adopting.md` shows a `pre-push` hook for it. Mention it in the
report; don't add it.
