/**
 * One code-quality gate for Java 21+ Gradle builds: google-java-format, Error Prone, NullAway with
 * JSpecify, SpotBugs and JaCoCo, at versions tested together, run by stlCheck and tuned by one
 * profile.
 *
 * <p>Apply {@code dev.balakmran.sterling} to a project that uses the {@code java} plugin. It
 * formats with google-java-format, finds bugs at compile time with Error Prone and NullAway,
 * analyses bytecode with SpotBugs and verifies test coverage with JaCoCo. {@link
 * dev.balakmran.sterling.Profile} sets how strict each one is, and the {@code sterling { }} block
 * ({@link dev.balakmran.sterling.SterlingExtension}) adjusts individual settings.
 *
 * <p>This package is Sterling's public API. {@code dev.balakmran.sterling.internal} is not.
 */
package dev.balakmran.sterling;
