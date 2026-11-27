# Security policy

## Supported versions

Only the latest minor release gets fixes, as a patch release of that minor.
Older minors don't: moving to the latest is one release at a time, with the
notes in [Upgrading Sterling](docs/guide/upgrading.md).

## Reporting a vulnerability

Report it privately, through GitHub's private vulnerability reporting: the
**Security** tab of [balakmran/sterling](https://github.com/balakmran/sterling),
then **Report a vulnerability**. Please don't open a public issue or pull
request for it.

Say which release you found it in, what it lets someone do, and how to
reproduce it. You'll get an answer as soon as it can be read, and a fix and
an advisory once it's understood.

## What's in scope

- **The Gradle plugin** (`dev.balakmran.sterling`): anything that makes a build
  that applies it run code, read files or send data it shouldn't.
- **The agent plugin** (`agent-plugins/sterling`): its skills, and the scripts
  they run, for the same kinds of problem.

A vulnerability in a tool Sterling configures (google-java-format, Spotless,
Error Prone, NullAway, SpotBugs, JaCoCo) belongs to that tool; report it
there. Sterling's part is moving to the fixed release, which a patch release
does once the tool has shipped it.

## Releases

From 0.3.0, Sterling's jars carry a build provenance attestation, made by the
release workflow, so you can check that a published release was built from this
repository. A release is published from a pull request that its maintainer
merges by hand after the full test matrix has passed.
