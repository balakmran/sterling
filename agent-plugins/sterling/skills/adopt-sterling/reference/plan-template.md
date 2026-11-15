# Plan template

Fill this in from detection and show it before the first change. Keep it to a
screen. Say "unknown" for anything detection couldn't tell.

```
Adopting Sterling <version> in <project name> (<new | existing>)

Build        Gradle <version>, daemon JDK <n>, toolchain <n>, <single | N modules>
Plugin goes  <version catalog + root build.gradle.kts | convention plugin <path>>
Profile      LENIENT first; STANDARD if `stlCheck` passes at the end
Formatting   format.style <GOOGLE | AOSP>   (4-space share: <n>%)
             <reformat commit + .git-blame-ignore-revs | format.ratchetFrom "<ref>">
Remove       <each duplicate: file, what, and what replaces it>
Coverage     measured per module, set to the floor: <module list>
Hook         <.pre-commit-config.yaml (new | existing) | .husky/pre-commit | lefthook.yml>
Left alone   <Checkstyle, PMD, other>
Unknown      <anything>

Commits
  1 build: add Sterling <version> at LENIENT
  2 style: reformat with google-java-format <version>
  3 chore: ignore the reformat in git blame
  4 build: let Sterling own the tool configuration
  5 build: set coverage thresholds from the measured value
  6 build: move to STANDARD            (only if the gate passes)
  7 chore: check formatting before every commit

Proceed?
```

For a new project the commits are `build: add Sterling <version>` and the hook.
