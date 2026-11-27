# Compatibility

What each Sterling release was tested with, newest first. The tool versions are
the ones Sterling pins (see [the tools](tools.md#pinned-tool-versions)), and
[Upgrading Sterling](upgrading.md) says what changed between releases.

| Sterling | Error Prone | NullAway | JSpecify | SpotBugs | JaCoCo | google-java-format | Spotless |
|---|---|---|---|---|---|---|---|
| 0.3.0 | 2.53.0 | 0.14.4 | 1.0.1 | 4.10.5 | 0.8.16 | 2.0.0 | 8.12.0 |
| 0.2.0 | 2.52.0 | 0.14.3 | 1.0.1 | 4.10.5 | 0.8.16 | 2.0.0 | 8.11.0 |
| 0.1.0 | 2.52.0 | 0.14.3 | 1.0.1 | 4.10.5 | 0.8.16 | 2.0.0 | 8.11.0 |

## Gradle and Java

Every release since 0.1.0:

| | Supported | Tested |
|---|---|---|
| Gradle | 9.0 or later | 9.0.0 and the latest stable at release time |
| Project toolchain | Java 21 or later | 21 and 25 |
| Gradle daemon JVM | Java 21 or later | 21 and 25 |

Java 17 isn't supported: Error Prone runs inside javac, and google-java-format
runs inside the Gradle daemon. A project can still ship older bytecode with
`options.release`. Sterling's own bytecode targets Java 17, so on a daemon older
than 21 it can say so rather than fail to load.

Before each release the next Gradle (a release candidate or nightly) and the
latest JDK are tried once. A problem found there is reported, not a promise:
the matrix above is what a release is held to.

## Not supported

- **Android.** Sterling configures the `java` plugin's source sets, which an
  Android project doesn't have.
- **Kotlin sources.** A module with Kotlin sources isn't analysed as Kotlin:
  Error Prone, NullAway and SpotBugs see only the Java.
- **Maven.** Sterling is a Gradle plugin.
