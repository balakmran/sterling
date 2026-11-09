package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** JaCoCo on the test task, verified by stlCov against the coverage thresholds. */
class CoverageTest {
  private static final String VERIFY = ":jacocoTestCoverageVerification";

  // abs(5) alone covers 50% of Calc's instructions and 50% of its branches.
  private static final String CALC =
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
      """;

  private static final String HALF_COVERED_TEST =
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
      """;

  private static final String FULLY_COVERED_TEST =
      """
      package app;

      import static org.junit.jupiter.api.Assertions.assertEquals;

      import org.junit.jupiter.api.Test;

      class CalcTest {
        @Test
        void everything() {
          assertEquals(1, new Calc().abs(-1));
          assertEquals(1, new Calc().abs(1));
          assertEquals(2, new Calc().twice(1));
        }
      }
      """;

  @TempDir Path dir;

  private Fixture fixture(String sterling) {
    return new Fixture(dir)
        .file(
            "build.gradle.kts",
            """
            plugins {
                java
                id("dev.balakmran.sterling.java")
            }

            repositories { mavenCentral() }

            testing { suites { named<JvmTestSuite>("test") { useJUnitJupiter() } } }

            """
                + sterling)
        .file("src/main/java/app/Calc.java", CALC)
        .file("src/test/java/app/CalcTest.java", HALF_COVERED_TEST);
  }

  private static String thresholds(int instruction, int branch) {
    return "sterling { coverage { instruction = " + instruction + "; branch = " + branch + " } }\n";
  }

  @Test
  void testRecordsCoverageAndWritesTheReports() {
    BuildResult result = fixture("").build("test");

    assertThat(outcome(result, ":jacocoTestReport")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(dir.resolve("build/jacoco/test.exec")).exists();
    assertThat(dir.resolve("build/reports/jacoco/test/jacocoTestReport.xml")).exists();
    assertThat(dir.resolve("build/reports/jacoco/test/html/index.html")).exists();
  }

  @Test
  void coverageBelowTheStandardThresholdsFailsStlCov() {
    BuildResult result = fixture("").buildAndFail("stlCov");

    assertThat(outcome(result, VERIFY)).isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains("instructions covered ratio is 0.50");
  }

  @Test
  void thresholdsAboveActualCoverageFailThenPassOnceLowered() {
    Fixture fixture = fixture(thresholds(90, 90));
    assertThat(outcome(fixture.buildAndFail("stlCov"), VERIFY)).isEqualTo(TaskOutcome.FAILED);

    fixture.file("build.gradle.kts", fixture.read("build.gradle.kts").replace("90", "40"));
    assertThat(outcome(fixture.build("stlCov"), VERIFY)).isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void raisingThresholdsAboveActualCoverageFailsRatherThanStayingUpToDate() {
    Fixture fixture = fixture(thresholds(40, 40));
    assertThat(outcome(fixture.build("stlCov"), VERIFY)).isEqualTo(TaskOutcome.SUCCESS);

    fixture.file("build.gradle.kts", fixture.read("build.gradle.kts").replace("40", "60"));
    assertThat(outcome(fixture.buildAndFail("stlCov"), VERIFY)).isEqualTo(TaskOutcome.FAILED);
  }

  @Test
  void excludingAnUncoveredClassLetsStlCovPass() {
    Fixture fixture =
        fixture("")
            .file("src/test/java/app/CalcTest.java", FULLY_COVERED_TEST)
            .file("src/main/java/app/Uncovered.java", CALC.replace("Calc", "Uncovered"));
    assertThat(outcome(fixture.buildAndFail("stlCov"), VERIFY)).isEqualTo(TaskOutcome.FAILED);

    fixture.file(
        "build.gradle.kts",
        fixture.read("build.gradle.kts")
            + "sterling { coverage { excludes.add(\"**/Uncovered*\") } }\n");
    BuildResult result = fixture.build("stlCov", "jacocoTestReport");

    assertThat(outcome(result, VERIFY))
        .describedAs(result.getOutput())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixture.read("build/reports/jacoco/test/jacocoTestReport.xml"))
        .contains("Calc")
        .doesNotContain("Uncovered");
  }

  @Test
  void stlCovStillVerifiesWhenTestIsRequestedToo() {
    BuildResult result = fixture(thresholds(40, 40)).build("test", "stlCov");

    assertThat(outcome(result, VERIFY)).isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void coverageDisabledRunsTestsWithoutTheAgentOrReports() {
    BuildResult result =
        fixture("sterling { coverage { enabled = false } }").build("test", "stlCov");

    assertThat(outcome(result, ":test")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":jacocoTestReport")).isNull();
    assertThat(outcome(result, VERIFY)).isNull();
    assertThat(Files.exists(dir.resolve("build/jacoco/test.exec"))).isFalse();
  }

  @Test
  void aThresholdAbove100FailsStlCovNamingTheSetting() {
    BuildResult result =
        fixture("sterling { coverage { instruction = 120 } }").buildAndFail("stlCov");

    assertThat(result.getOutput())
        .contains("sterling.coverage.instruction must be between 0 and 100 (was 120).");
  }
}
