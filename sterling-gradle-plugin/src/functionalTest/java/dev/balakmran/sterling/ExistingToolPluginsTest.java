package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A project that applies Spotless or SpotBugs itself, as a hand-written build-logic often does. */
class ExistingToolPluginsTest {
  private static final String FULL_SPOTBUGS_MESSAGE =
      "Sterling applies SpotBugs itself, with com.github.spotbugs-base, and registers spotbugsMain."
          + " Remove the com.github.spotbugs plugin; spotbugs { } settings keep working.";

  private static final String APP =
      """
      package app;

      public class App {
        public static void main(String[] args) {}
      }
      """;

  @TempDir Path dir;

  private Fixture fixture(String plugins, String script) {
    return new Fixture(dir)
        .file(
            "build.gradle.kts",
            "plugins {\n" + plugins + "\n}\n\nrepositories { mavenCentral() }\n\n" + script)
        .file("src/main/java/app/App.java", APP);
  }

  @Test
  void theFullSpotbugsPluginAppliedFirstFailsWithSterlingsMessage() {
    BuildResult result =
        fixture(
                """
                java
                id("com.github.spotbugs")
                id("dev.balakmran.sterling")
                """,
                "")
            .buildAndFail("help");

    assertThat(result.getOutput()).contains(FULL_SPOTBUGS_MESSAGE);
  }

  @Test
  void theFullSpotbugsPluginAppliedAfterSterlingFailsWithSterlingsMessage() {
    BuildResult result =
        fixture(
                """
                id("dev.balakmran.sterling")
                id("com.github.spotbugs")
                java
                """,
                "")
            .buildAndFail("help");

    assertThat(result.getOutput()).contains(FULL_SPOTBUGS_MESSAGE);
  }

  @Test
  void theFullSpotbugsPluginAfterJavaAndSterlingFailsInItsOwnApply() {
    // Sterling has registered spotbugsMain by then, and the plugin registers it before any hook.
    BuildResult result =
        fixture(
                """
                java
                id("dev.balakmran.sterling")
                id("com.github.spotbugs")
                """,
                "")
            .buildAndFail("help");

    assertThat(result.getOutput())
        .contains("Failed to apply plugin 'com.github.spotbugs'")
        .contains("Cannot add task 'spotbugsMain'");
  }

  @Test
  void spotlessAppliedByTheProjectWorksAlongsideSterlingsInEitherOrder() {
    for (String plugins :
        new String[] {
          "java\nid(\"com.diffplug.spotless\")\nid(\"dev.balakmran.sterling\")",
          "java\nid(\"dev.balakmran.sterling\")\nid(\"com.diffplug.spotless\")"
        }) {
      BuildResult result =
          fixture(plugins, "spotless { java { trimTrailingWhitespace() } }").build("stlFmtCheck");

      // The second order reuses the first's up-to-date check: the same steps, the same files.
      assertThat(outcome(result, ":spotlessJavaCheck"))
          .describedAs(plugins)
          .isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
    }
  }

  @Test
  void sterlingsStepsFromAConventionPluginFailWithSterlingsMessage() {
    for (String step : new String[] {"googleJavaFormat()", "formatAnnotations()"}) {
      // Configured before Sterling applies, as a build-logic convention plugin does.
      BuildResult result =
          fixture(
                  """
                  java
                  id("com.diffplug.spotless")
                  id("dev.balakmran.sterling") apply false
                  """,
                  "spotless { java { "
                      + step
                      + " } }\n\napply(plugin = \"dev.balakmran.sterling\")\n")
              .buildAndFail("stlFmtCheck");

      assertThat(result.getOutput())
          .describedAs(step)
          .contains(
              "Sterling adds google-java-format "
                  + System.getProperty("sterling.googleJavaFormatVersion")
                  + " and formatAnnotations() to Spotless itself. Remove both from the project's"
                  + " spotless { java { } } block; its other steps keep working.");
    }
  }

  @Test
  void googleJavaFormatInTheBuildScriptFailsWithSpotlessNamingTheDuplicate() {
    // Sterling's step is added first, so the project's call fails inside Spotless.
    BuildResult result =
        fixture("java\nid(\"dev.balakmran.sterling\")", "spotless { java { googleJavaFormat() } }")
            .buildAndFail("stlFmtCheck");

    assertThat(result.getOutput())
        .contains("Multiple steps with name 'google-java-format' for spotless format 'java'");
  }
}
