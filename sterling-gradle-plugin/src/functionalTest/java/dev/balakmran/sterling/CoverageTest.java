package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** JaCoCo on the test tasks, verified by stlCov against the coverage thresholds. */
class CoverageTest {
  private static final String VERIFY = ":jacocoTestCoverageVerification";
  private static final String SUMMARY = ":stlCovSummary";

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
                id("dev.balakmran.sterling")
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
  void aFailedVerificationPrintsTheMeasurementsAndTheMostMissedFiles() {
    BuildResult result =
        fixture("")
            .file("src/main/java/app/Uncovered.java", CALC.replace("Calc", "Uncovered"))
            .buildAndFail("stlCov");

    assertThat(outcome(result, VERIFY)).isEqualTo(TaskOutcome.FAILED);
    assertThat(outcome(result, SUMMARY)).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput())
        .contains("Coverage is below the thresholds:")
        .containsPattern("(?m)^  instruction: [0-9.]+% \\(below minimum 80%\\)$")
        .containsPattern("(?m)^  branch: [0-9.]+% \\(below minimum 70%\\)$")
        .containsPattern("(?m)^app/Uncovered\\.java \\[JaCoCo:INSTRUCTION\\] (\\d+) of \\1 missed$")
        .containsPattern("(?m)^app/Calc\\.java \\[JaCoCo:INSTRUCTION\\] \\d+ of \\d+ missed$");
    // Most missed first.
    assertThat(result.getOutput().indexOf("app/Uncovered.java"))
        .isLessThan(result.getOutput().indexOf("app/Calc.java"));
  }

  @Test
  void aPassingVerificationPrintsNoSummary() {
    // A failed run first, so a marker left behind would show up in the passing run.
    Fixture fixture = fixture(thresholds(90, 90));
    assertThat(outcome(fixture.buildAndFail("stlCov"), SUMMARY)).isEqualTo(TaskOutcome.SUCCESS);

    // A different length, so the configuration cache sees the script change.
    fixture.file("build.gradle.kts", fixture.read("build.gradle.kts").replace("90", "5"));
    BuildResult result = fixture.build("stlCov");

    assertThat(outcome(result, VERIFY)).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, SUMMARY)).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput()).doesNotContain("Coverage is below the thresholds");
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

  // Spring Initializr's Kotlin template creates the test task eagerly, before sterling { } runs.
  private static final String EAGER_TEST = "tasks.withType<Test> { }\n";

  @Test
  void coverageOffAppliesEvenWhenTheTestTaskIsCreatedFirst() {
    BuildResult result =
        fixture(EAGER_TEST + "sterling { coverage { enabled = false } }\n").build("test");

    assertThat(outcome(result, ":test")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(Files.exists(dir.resolve("build/jacoco/test.exec"))).isFalse();
  }

  @Test
  void coverageOnUnderLenientStillFailsWhenTheTestTaskIsCreatedFirst() {
    BuildResult result =
        fixture(EAGER_TEST + "sterling { coverage { enabled = true; instruction = 90 } }\n")
            .file("gradle.properties", "sterling.profile=lenient\n")
            .buildAndFail("stlCov");

    assertThat(outcome(result, VERIFY)).isEqualTo(TaskOutcome.FAILED);
  }

  @Test
  void thresholdsApplyEvenWhenTheVerificationTaskIsCreatedFirst() {
    BuildResult result =
        fixture("tasks.withType<JacocoCoverageVerification> { }\n" + thresholds(40, 40))
            .build("stlCov");

    assertThat(outcome(result, VERIFY)).isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void coverageDisabledRunsTestsWithoutTheAgentOrReports() {
    BuildResult result =
        fixture("sterling { coverage { enabled = false } }").build("test", "stlCov");

    assertThat(outcome(result, ":test")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":jacocoTestReport")).isNull();
    assertThat(outcome(result, VERIFY)).isNull();
    assertThat(outcome(result, SUMMARY)).isNull();
    assertThat(Files.exists(dir.resolve("build/jacoco/test.exec"))).isFalse();
  }

  // An integration suite covering the half of Calc that CalcTest misses.
  private Fixture withIntegrationTests(String sterling) {
    return fixture(
            """
            testing {
                suites {
                    register<JvmTestSuite>("integrationTest") {
                        useJUnitJupiter()
                        dependencies { implementation(project()) }
                    }
                }
            }

            """
                + sterling)
        .file(
            "src/integrationTest/java/app/CalcIT.java",
            """
            package app;

            import static org.junit.jupiter.api.Assertions.assertEquals;

            import org.junit.jupiter.api.Test;

            class CalcIT {
              @Test
              void rest() {
                assertEquals(1, new Calc().abs(-1));
                assertEquals(2, new Calc().twice(1));
              }
            }
            """);
  }

  @Test
  void everyTestTaskCountsByDefault() {
    Fixture fixture = withIntegrationTests("");

    BuildResult result = fixture.build("stlCov");

    assertThat(outcome(result, VERIFY))
        .describedAs(result.getOutput())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":test")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":integrationTest")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":jacocoTestReport")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(dir.resolve("build/jacoco/test.exec")).exists();
    assertThat(dir.resolve("build/jacoco/integrationTest.exec")).exists();
    // Calc is fully covered only when both runs count.
    assertThat(fixture.read("build/reports/jacoco/test/jacocoTestReport.xml"))
        .contains("<counter type=\"INSTRUCTION\" missed=\"0\"")
        .contains("<counter type=\"BRANCH\" missed=\"0\" covered=\"2\"/>");
  }

  @Test
  void testTasksNarrowsWhichRunsCount() {
    BuildResult result =
        withIntegrationTests("sterling { coverage { testTasks = setOf(\"test\") } }")
            .buildAndFail("stlCov");

    assertThat(outcome(result, VERIFY)).isEqualTo(TaskOutcome.FAILED);
    assertThat(outcome(result, ":test")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":integrationTest")).isNull();
  }

  @Test
  void runningOneTestTaskDoesntRunTheOthers() {
    BuildResult result = withIntegrationTests("").build("test");

    assertThat(outcome(result, ":test")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":jacocoTestReport")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":integrationTest")).isNull();
  }

  @Test
  void stlInfoStillPrintsWithAnUnknownTestTask() {
    BuildResult result =
        withIntegrationTests("sterling { coverage { testTasks = setOf(\"nope\") } }")
            .build("stlInfo");

    assertThat(result.getOutput()).contains("branch >= 70%; nope)");
  }

  @Test
  void theSummaryFollowsAMovedBuildDirectory() {
    BuildResult result =
        fixture(
                "tasks.withType<JacocoCoverageVerification> { }\nlayout.buildDirectory = file(\"out\")\n")
            .buildAndFail("stlCov");

    assertThat(outcome(result, VERIFY)).isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains("Coverage is below the thresholds:");
  }

  @Test
  void anUncountedTestTaskRunsWithoutTheAgent() {
    BuildResult result =
        withIntegrationTests("sterling { coverage { testTasks = setOf(\"test\") } }")
            .build("integrationTest");

    assertThat(outcome(result, ":integrationTest")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":jacocoTestReport")).isNull();
    assertThat(Files.exists(dir.resolve("build/jacoco/integrationTest.exec"))).isFalse();
  }

  @Test
  void anUnknownTestTaskFailsListingTheTestTasks() {
    BuildResult result =
        withIntegrationTests("sterling { coverage { testTasks = setOf(\"test\", \"nope\") } }")
            .buildAndFail("stlCov");

    assertThat(result.getOutput())
        .contains(
            "sterling.coverage.testTasks names nope, which isn't a Test task. The Test tasks are:"
                + " integrationTest, test.");
  }

  @Test
  void coverageOffRecordsNothingFromAnyTestTask() {
    BuildResult result =
        withIntegrationTests("sterling { coverage { enabled = false } }")
            .build("test", "integrationTest", "stlCov");

    assertThat(outcome(result, ":integrationTest")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":jacocoTestReport")).isNull();
    assertThat(Files.exists(dir.resolve("build/jacoco/test.exec"))).isFalse();
    assertThat(Files.exists(dir.resolve("build/jacoco/integrationTest.exec"))).isFalse();
  }

  @Test
  void stlInfoListsTheCountedTestTasks() {
    assertThat(withIntegrationTests("").build("stlInfo").getOutput())
        .contains("branch >= 70%; integrationTest, test)");
  }

  @Test
  void aThresholdAbove100FailsStlCovNamingTheSetting() {
    BuildResult result =
        fixture("sterling { coverage { instruction = 120 } }").buildAndFail("stlCov");

    assertThat(result.getOutput())
        .contains("sterling.coverage.instruction must be between 0 and 100 (was 120).");
  }
}
