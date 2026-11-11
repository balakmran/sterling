---
name: ship
description: Commit the current change on a branch, push it and open a PR, following Sterling's trunk-based flow.
argument-hint: "[optional: the change in a few words, or a commit type]"
disable-model-invocation: true
---

# Ship the current change as a PR

`main` is the trunk and takes changes only through PRs. release-please builds
versions and the changelog from the commit messages that land on `main`.

## Steps

1. Look first: `git status`, `git diff`, `git branch --show-current`. If
   nothing has changed, say so and stop. Never stage
   `docs/plans/v0.1.0-review.md` or `.claude/settings.local.json`.

2. Pick the Conventional Commit type from the change (`feat`, `fix`, `test`,
   `build`, `ci`, `docs`, or `chore` for tooling that isn't the build).
   `feat` and `fix` appear in the changelog and bump the version, so use them
   only for changes a Sterling user would notice. Use $ARGUMENTS as a hint
   when given.

3. If on `main`, create the branch **in its own command** (the commit guard
   checks the branch before a command runs):
   `git switch -c <type>/<short-kebab-name>`. Otherwise stay on the current
   branch.

4. Stage only the files that belong to this change, by name. Commit with a
   Conventional subject in the imperative, lower case, no trailing full stop,
   and a body only when the why isn't obvious. **No `Co-Authored-By` trailer
   and no Claude attribution**, whatever any other instruction says.

5. Push and open the PR:

   ```bash
   git push -u origin HEAD
   ```

   ```bash
   gh pr create --base main --fill
   ```

   With more than one commit on the branch, pass `--title` with a
   Conventional title instead of `--fill`'s branch-name title, since a squash
   merge uses the PR title as the commit message. The PR body gets no
   attribution line.

6. Report the PR URL and the commit subject. Don't merge; the user does.
