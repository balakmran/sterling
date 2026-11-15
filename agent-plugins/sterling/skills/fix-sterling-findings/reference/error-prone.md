# Error Prone findings

Compile output has lines like
`Greeter.java:28: error: [ArrayEquals] Reference equality used to compare arrays`.
`findings.sh` lists them with the check name. Error-level findings fail
compilation in every profile; at `STRICT`, warnings do too, javac's own
(`[removal]`, `[deprecation]`) included.

- Read the check's explanation: `https://errorprone.info/bugpattern/<CheckName>`.
  It says what's wrong and the fix, and the diagnostic's own "Did you mean"
  line gives it for many checks.
- Fix the code. A rename, an `equals` call, an explicit charset or time zone,
  a `final` field: the checks name what to change.
- Never `@SuppressWarnings` a check to move on. Do it only when the code is
  right and the check is wrong, with a comment that says why.
- Never call `errorProne { disable("CheckName") }`: that is a project
  decision, in the build file, which this skill doesn't touch. Say in the
  report which check you'd disable and why.
- Generated sources under `build/generated` are skipped by Sterling; if a
  finding names one, the generator's input is the thing to fix, so report it.
