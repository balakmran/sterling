package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Without java: the stl tasks, profiles, settings and stlInfo. */
class LifecycleTasksTest {
  @TempDir Path dir;

  private Fixture fixture(String script) {
    return new Fixture(dir)
        .file(
            "build.gradle.kts",
            """
            import dev.balakmran.sterling.Profile

            plugins { id("dev.balakmran.sterling") }

            """
                + script);
  }

  @Test
  void registersEveryStlTaskInTheSterlingGroupWithADescription() {
    BuildResult result = fixture("").build("tasks", "--group", "sterling");

    String section = result.getOutput().split("Sterling tasks", 2)[1];
    List.of("stlFmt", "stlFmtCheck", "stlLint", "stlSec", "stlCov", "stlCheck", "stlInfo")
        .forEach(task -> assertThat(section).containsPattern("(?m)^" + task + " - \\S.+$"));
  }

  @Test
  void stlCheckAggregatesTheVerifyTasks() {
    BuildResult result = fixture("").build("stlCheck");

    List.of("stlFmtCheck", "stlLint", "stlSec", "stlCov", "stlCheck")
        .forEach(task ->
            assertThat(outcome(result, ":" + task))
                .describedAs(task)
                .isEqualTo(TaskOutcome.UP_TO_DATE));
    assertThat(outcome(result, ":stlFmt")).isNull();
  }

  @Test
  void aThresholdAbove100FailsStlInfoNamingTheSetting() {
    BuildResult result =
        fixture("sterling { coverage { instruction = 120 } }").buildAndFail("stlInfo");

    assertThat(result.getOutput())
        .contains("sterling.coverage.instruction must be between 0 and 100 (was 120).");
  }

  @Test
  void profilePropertySetsTheProfileCommandLineOverridesAndInvalidFails() {
    Fixture fixture = fixture("").file("gradle.properties", "sterling.profile=strict\n");

    BuildResult strict = fixture.build("stlInfo");
    assertThat(outcome(strict, ":stlInfo")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(strict.getOutput()).contains("Profile:              STRICT");

    BuildResult lenient = fixture.build("stlInfo", "-Psterling.profile=lenient");
    assertThat(lenient.getOutput()).contains("Profile:              LENIENT");

    BuildResult bogus = fixture.buildAndFail("stlInfo", "-Psterling.profile=bogus");
    assertThat(bogus.getOutput())
        .contains("Unknown sterling.profile 'bogus'. Allowed values: lenient, standard, strict.");
  }

  @Test
  void explicitValuesOverrideProfileConventions() {
    BuildResult result =
        fixture(
                """
                sterling {
                    profile = Profile.LENIENT
                    attachToCheck = true
                    errorProne { disable("UnusedVariable") }
                    spotbugs { enabled = true }
                    coverage {
                        enabled = true
                        instruction = 90
                        excludes.add("**/*Application*")
                    }
                }
                """)
            .file("gradle.properties", "sterling.profile=strict\n")
            .build("stlInfo");

    assertThat(result.getOutput())
        .contains("Profile:              LENIENT")
        .contains("Gate wired to check:  yes")
        .contains("warnings are not errors; 1 check disabled)")
        .containsPattern("SpotBugs:\\s+on ")
        .contains("instruction >= 90%, branch >= 0%; 1 exclude)");
  }

  @Test
  void theSameBlockWorksFromTheGroovyDsl() {
    BuildResult result =
        new Fixture(dir)
            .file(
                "build.gradle",
                """
                plugins { id 'dev.balakmran.sterling' }

                sterling {
                    profile = dev.balakmran.sterling.Profile.STRICT
                    errorProne { disable('UnusedVariable') }
                    coverage { instruction = 50 }
                }
                """)
            .build("stlInfo");

    assertThat(result.getOutput())
        .contains("Profile:              STRICT")
        .contains("warnings are errors; 1 check disabled)")
        .contains("instruction >= 50%, branch >= 85%");
  }
}
