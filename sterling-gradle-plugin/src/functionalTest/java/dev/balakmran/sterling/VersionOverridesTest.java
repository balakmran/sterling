package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A project's own tool versions win over Sterling's pins, and stlInfo says so. */
class VersionOverridesTest {
  // Older releases than Sterling pins, so the override is visible whichever way Gradle resolves.
  private static final String ERROR_PRONE = "2.42.0";
  private static final String NULLAWAY = "0.12.10";
  private static final String JSPECIFY = "1.0.0";
  private static final String SPOTBUGS = "4.9.8";
  private static final String JACOCO = "0.8.13";

  @TempDir Path dir;

  private Fixture fixture() {
    return new Fixture(dir)
        .file(
            "build.gradle.kts",
            """
            plugins {
                java
                id("dev.balakmran.sterling")
            }

            repositories { mavenCentral() }

            dependencies {
                errorprone("com.google.errorprone:error_prone_core:%s")
                errorprone("com.uber.nullaway:nullaway:%s")
                compileOnly("org.jspecify:jspecify:%s")
            }

            spotbugs { toolVersion = "%s" }
            jacoco { toolVersion = "%s" }
            """
                .formatted(ERROR_PRONE, NULLAWAY, JSPECIFY, SPOTBUGS, JACOCO));
  }

  private static String pinned(String tool) {
    return System.getProperty("sterling." + tool + "Version");
  }

  private static String resolved(Fixture fixture, String configuration) {
    return fixture.build("dependencies", "--configuration", configuration).getOutput();
  }

  @Test
  void eachToolResolvesTheProjectsVersion() {
    Fixture fixture = fixture();

    assertThat(resolved(fixture, "annotationProcessor"))
        .contains("com.google.errorprone:error_prone_core:" + ERROR_PRONE + "\n")
        .contains("com.uber.nullaway:nullaway:" + NULLAWAY + "\n")
        .doesNotContain("error_prone_core:" + pinned("errorprone"))
        .doesNotContain("nullaway:" + pinned("nullaway"));
    assertThat(resolved(fixture, "compileClasspath"))
        .contains("org.jspecify:jspecify:" + JSPECIFY + "\n")
        .doesNotContain("jspecify:" + pinned("jspecify"));
    assertThat(resolved(fixture, "spotbugs"))
        .contains("com.github.spotbugs:spotbugs:" + SPOTBUGS)
        .doesNotContain("com.github.spotbugs:spotbugs:" + pinned("spotbugs"));
    assertThat(resolved(fixture, "jacocoAgent"))
        .contains("org.jacoco:org.jacoco.agent:" + JACOCO)
        .doesNotContain("org.jacoco.agent:" + pinned("jacoco"));
  }

  @Test
  void stlInfoShowsEachOverride() {
    BuildResult result = fixture().build("stlInfo");

    assertThat(outcome(result, ":stlInfo")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput())
        .contains("Error Prone:          on   (" + ERROR_PRONE + " (project override); ")
        .contains(
            "NullAway:             on   ("
                + NULLAWAY
                + " (project override); JSpecify "
                + JSPECIFY
                + " (project override); ")
        .contains("SpotBugs:             on   (" + SPOTBUGS + " (project override); ")
        .contains("(JaCoCo " + JACOCO + " (project override); ");
  }

  @Test
  void stlInfoShowsNoOverrideForSterlingsOwnVersions() {
    BuildResult result =
        new Fixture(dir)
            .file(
                "build.gradle.kts",
                """
                plugins {
                    java
                    id("dev.balakmran.sterling")
                }
                """)
            .build("stlInfo");

    assertThat(result.getOutput()).doesNotContain("project override");
  }
}
