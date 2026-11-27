package dev.balakmran.sterling.internal;

/**
 * One finding or shortfall, as reported through Gradle's Problems API.
 *
 * @param name the finding's kind as a Gradle problem name: lower case, with hyphens
 * @param displayName the kind as people read it, such as SpotBugs's pattern
 * @param message what's wrong, specific to this finding
 * @param file the absolute path of the source file, or null when it's unknown
 * @param line the 1-based line, or 0 when there is none
 * @param details more about the finding, or null
 * @param error whether it's an error rather than a warning, on Gradle versions that keep severity
 */
record Finding(
    String name,
    String displayName,
    String message,
    String file,
    int line,
    String details,
    boolean error) {}
