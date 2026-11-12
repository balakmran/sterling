package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Whether {@code check}, and so {@code build}, runs the gate, following attachToCheck. */
class GateTest {
  // Formatted, free of Error Prone and SpotBugs findings, and fully covered by its test.
  private static final String CALC =
      """
      package app;

      public class Calc {
        public int twice(int x) {
          return 2 * x;
        }
      }
      """;

  private static final String CALC_TEST =
      """
      package app;

      import static org.junit.jupiter.api.Assertions.assertEquals;

      import org.junit.jupiter.api.Test;

      class CalcTest {
        @Test
        void twice() {
          assertEquals(4, new Calc().twice(2));
        }
      }
      """;

  @TempDir Path dir;

  private Fixture javaProject(String sterling) {
    return new Fixture(dir)
        .file(
            "build.gradle.kts",
            """
            plugins {
                java
                id("dev.balakmran.sterling")
            }

            repositories { mavenCentral() }

            testing { suites { named<JvmTestSuite>("test") { useJUnitJupiter() } } }

            """
                + sterling)
        .file("src/main/java/app/Calc.java", CALC)
        .file("src/test/java/app/CalcTest.java", CALC_TEST);
  }

  private Fixture baseProject(String sterling) {
    return new Fixture(dir)
        .file(
            "build.gradle.kts",
            """
            import dev.balakmran.sterling.Profile

            plugins { id("dev.balakmran.sterling") }

            """
                + sterling);
  }

  @Test
  void byDefaultCheckRunsTheGateAndEveryEnabledTool() {
    BuildResult result = javaProject("").build("check");

    assertThat(outcome(result, ":stlCheck")).isNotNull();
    List.of(
            ":spotlessCheck",
            ":compileJava",
            ":compileTestJava",
            ":spotbugsMain",
            ":jacocoTestCoverageVerification")
        .forEach(task ->
            assertThat(outcome(result, task))
                .describedAs(task)
                .isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE));
  }

  @Test
  void lenientCheckSkipsTheGateButStlCheckStillRuns() {
    Fixture fixture = baseProject("sterling { profile = Profile.LENIENT }");

    assertThat(outcome(fixture.build("check"), ":stlCheck")).isNull();
    assertThat(outcome(fixture.build("stlCheck"), ":stlCheck")).isNotNull();
  }

  @Test
  void attachToCheckOffDetachesTheGateFromStandard() {
    BuildResult result = baseProject("sterling { attachToCheck = false }").build("check");

    assertThat(outcome(result, ":stlCheck")).isNull();
  }

  @Test
  void attachToCheckOnAttachesTheGateToLenient() {
    BuildResult result =
        baseProject("sterling {\n    profile = Profile.LENIENT\n    attachToCheck = true\n}\n")
            .build("check");

    assertThat(outcome(result, ":stlCheck")).isNotNull();
  }
}
