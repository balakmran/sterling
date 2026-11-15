package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** stlInfo --json: one line of JSON per project, for scripts and coding agents. */
class InfoJsonTest {
  private static final String JAVA_AND_STERLING =
      """
      import dev.balakmran.sterling.Profile

      plugins {
          java
          id("dev.balakmran.sterling")
      }

      repositories { mavenCentral() }
      """;

  @TempDir Path dir;

  // The JSON lines in a build's output, one per project.
  private static List<String> json(BuildResult result) {
    return result
        .getOutput()
        .lines()
        .filter(line -> line.startsWith("{\"schemaVersion\":"))
        .toList();
  }

  @Test
  void aJavaProjectGetsOneLineWithTheToolchainAndReportPaths() {
    BuildResult result =
        new Fixture(dir)
            .file("build.gradle.kts", JAVA_AND_STERLING)
            .file("src/main/java/App.java", "public class App {}\n")
            .build("-q", "stlInfo", "--json");

    assertThat(outcome(result, ":stlInfo")).isEqualTo(TaskOutcome.SUCCESS);
    // The report paths are read without running the tasks that write them.
    for (String task : List.of(":compileJava", ":spotbugsMain", ":test", ":jacocoTestReport")) {
      assertThat(outcome(result, task)).describedAs(task).isNull();
    }
    assertThat(json(result))
        .singleElement()
        .asString()
        .contains("\"project\":\":\",\"profile\":\"STANDARD\",\"profileProperty\":null")
        .contains("\"javaToolchain\":{\"active\":true,\"version\":" + Fixture.javaVersion() + "}")
        .contains(
            "\"reports\":{\"sarif\":\"build/reports/spotbugs/main.sarif\","
                + "\"xml\":\"build/reports/spotbugs/main.xml\"}")
        .contains("\"reports\":{\"xml\":\"build/reports/jacoco/test/jacocoTestReport.xml\"}")
        .contains("\"excludes\":[],\"testTasks\":[\"test\"]");
    assertThat(result.getOutput()).doesNotContain("Profile:");
  }

  @Test
  void explicitSettingsAndTheProfilePropertyAreReported() {
    BuildResult result =
        new Fixture(dir)
            .file(
                "build.gradle.kts",
                JAVA_AND_STERLING
                    + """
                    sterling {
                        errorProne { disable("UnusedVariable") }
                        spotbugs { excludeFilter = file("config/spotbugs-exclude.xml") }
                        coverage { instruction = 60 }
                    }
                    """)
            .build("-q", "stlInfo", "--json", "-Psterling.profile=lenient");

    assertThat(json(result))
        .singleElement()
        .asString()
        .contains("\"profile\":\"LENIENT\",\"profileProperty\":\"lenient\",\"attachToCheck\":false")
        .contains("\"disabledChecks\":[\"UnusedVariable\"]")
        .contains("\"excludeFilter\":\"config/spotbugs-exclude.xml\"")
        .contains("\"instruction\":60,\"branch\":0");
  }

  @Test
  void eachModulePrintsItsOwnLine() {
    String module = "plugins {\n    java\n    id(\"dev.balakmran.sterling\")\n}\n";
    BuildResult result =
        new Fixture(dir)
            .file("settings.gradle.kts", "rootProject.name = \"consumer\"\ninclude(\"a\", \"b\")\n")
            .file("a/build.gradle.kts", module)
            .file("b/build.gradle.kts", module)
            .build("-q", "stlInfo", "--json");

    assertThat(json(result))
        .hasSize(2)
        .anySatisfy(line -> assertThat(line).contains("\"project\":\":a\""))
        .anySatisfy(line -> assertThat(line).contains("\"project\":\":b\""));
  }

  @Test
  void withoutJavaTheToolchainIsInactiveAndThereAreNoReports() {
    BuildResult result =
        new Fixture(dir)
            .file("build.gradle.kts", "plugins { id(\"dev.balakmran.sterling\") }\n")
            .build("-q", "stlInfo", "--json");

    assertThat(json(result))
        .singleElement()
        .asString()
        .contains("\"javaToolchain\":{\"active\":false,\"reason\":\"java plugin not applied\"}")
        .doesNotContain("\"reports\"");
  }

  @Test
  void errorProneOffLeavesNullAwayEnabledButInactive() {
    BuildResult result =
        new Fixture(dir)
            .file(
                "build.gradle.kts",
                JAVA_AND_STERLING + "sterling { errorProne { enabled = false } }\n")
            .build("-q", "stlInfo", "--json");

    assertThat(json(result))
        .singleElement()
        .asString()
        .contains("\"nullAway\":{\"enabled\":true,\"active\":false");
  }

  @Test
  void anInvalidThresholdFailsAsInTextMode() {
    BuildResult result =
        new Fixture(dir)
            .file(
                "build.gradle.kts",
                "plugins { id(\"dev.balakmran.sterling\") }\n"
                    + "sterling { coverage { instruction = 120 } }\n")
            .buildAndFail("stlInfo", "--json");

    // Validation runs while the configuration is stored, before stlInfo would run.
    assertThat(outcome(result, ":stlInfo")).isNull();
    assertThat(result.getOutput())
        .contains("sterling.coverage.instruction must be between 0 and 100 (was 120).");
  }

  @Test
  void aSecondRunReusesTheConfigurationCache() {
    Fixture fixture = new Fixture(dir).file("build.gradle.kts", JAVA_AND_STERLING);
    fixture.build("stlInfo", "--json");

    BuildResult second = fixture.build("stlInfo", "--json");

    assertThat(second.getOutput()).contains("Configuration cache entry reused.");
    assertThat(json(second)).singleElement().asString().contains("\"schemaVersion\":1");
  }
}
