package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SpotBugs findings and coverage shortfalls reported through Gradle's Problems API. */
class ProblemsTest {
  private static final String PROBLEMS_REPORT = "build/reports/problems/problems-report.html";

  // Dereferences a value that is always null: NP_ALWAYS_NULL.
  private static final String BAD =
      """
      package app;

      public class Bad {
        public int length() {
          String text = null;
          return text.length();
        }
      }
      """;

  @TempDir Path dir;

  private Fixture fixture(String script) {
    return new Fixture(dir)
        .file(
            "build.gradle.kts",
            """
            plugins {
                java
                id("dev.balakmran.sterling")
            }

            repositories { mavenCentral() }

            """
                + script);
  }

  @Test
  void aSpotbugsFindingIsInTheProblemsReportAndStillPrintedOnTheConsole() {
    Fixture fixture = fixture("").file("src/main/java/app/Bad.java", BAD);

    BuildResult result = fixture.buildAndFail("stlSec");

    assertThat(outcome(result, ":stlSecSummary")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput())
        .containsPattern("(?m)^src/main/java/app/Bad\\.java:6 \\[SpotBugs:NP_ALWAYS_NULL\\] ");
    String report = fixture.read(PROBLEMS_REPORT);
    assertThat(report)
        .contains("\"name\":\"sterling\",\"displayName\":\"Sterling\"")
        .contains("\"name\":\"np-always-null\",\"displayName\":\"NP_ALWAYS_NULL\"")
        .contains("Null pointer dereference of text")
        .contains("SpotBugs rank ")
        .contains("src/main/java/app/Bad.java\",\"line\":6");
  }

  @Test
  void aSecondRunReusingTheConfigurationCacheReportsAgain() {
    Fixture fixture = fixture("").file("src/main/java/app/Bad.java", BAD);
    fixture.buildAndFail("stlSec");

    BuildResult second = fixture.buildAndFail("stlSec", "--rerun-tasks");

    assertThat(second.getOutput()).contains("Reusing configuration cache.");
    assertThat(fixture.read(PROBLEMS_REPORT)).contains("np-always-null");
  }

  @Test
  void aCoverageShortfallIsInTheProblemsReport() {
    Fixture fixture =
        fixture("testing { suites { named<JvmTestSuite>(\"test\") { useJUnitJupiter() } } }\n")
            .file(
                "src/main/java/app/Calc.java",
                """
                package app;

                public class Calc {
                  public int abs(int x) {
                    if (x < 0) {
                      return -x;
                    }
                    return x;
                  }

                  public int twice(int x) {
                    return 2 * x;
                  }
                }
                """)
            .file(
                "src/test/java/app/CalcTest.java",
                """
                package app;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                import org.junit.jupiter.api.Test;

                class CalcTest {
                  @Test
                  void abs() {
                    assertEquals(5, new Calc().abs(5));
                  }
                }
                """);

    BuildResult result = fixture.buildAndFail("stlCov");

    assertThat(outcome(result, ":stlCovSummary")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixture.read(PROBLEMS_REPORT))
        .contains(
            "\"name\":\"coverage-instruction\",\"displayName\":\"JaCoCo instruction coverage\"")
        .contains("\"name\":\"coverage-missed-instructions\"")
        .contains("src/main/java/app/Calc.java");
  }
}
