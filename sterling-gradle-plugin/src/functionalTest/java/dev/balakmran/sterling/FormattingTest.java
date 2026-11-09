package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spotless with google-java-format, run by stlFmt and stlFmtCheck behind the format flag. */
class FormattingTest {
  private static final String UNFORMATTED =
      """
      import java.util.List;
      public class App {   void f( ) { } ; }
      """;

  private static final String FORMATTED =
      """
      public class App {
        void f() {}
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
                id("dev.balakmran.sterling.java")
            }

            repositories { mavenCentral() }

            """
                + script);
  }

  private static boolean ranSpotless(BuildResult result) {
    return result.getTasks().stream()
        .map(BuildTask::getPath)
        .anyMatch(p -> p.startsWith(":spotless"));
  }

  @Test
  void anUnformattedFileFailsStlFmtCheck() {
    BuildResult result =
        fixture("").file("src/main/java/App.java", UNFORMATTED).buildAndFail("stlFmtCheck");

    assertThat(outcome(result, ":spotlessJavaCheck")).isEqualTo(TaskOutcome.FAILED);
    assertThat(outcome(result, ":stlFmtCheck")).isNull();
  }

  @Test
  void stlFmtReformatsDropsRedundantSemicolonsAndUnusedImportsThenTheCheckPasses() {
    Fixture fixture = fixture("").file("src/main/java/App.java", UNFORMATTED);

    BuildResult apply = fixture.build("stlFmt");
    assertThat(outcome(apply, ":spotlessJavaApply")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixture.read("src/main/java/App.java")).isEqualTo(FORMATTED);

    BuildResult check = fixture.build("stlFmtCheck");
    assertThat(outcome(check, ":spotlessJavaCheck"))
        .isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
    assertThat(outcome(check, ":stlFmtCheck")).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
  }

  @Test
  void formatDisabledStlFmtAndStlFmtCheckDontRunSpotless() {
    Fixture fixture =
        fixture("sterling { format { enabled = false } }")
            .file("src/main/java/App.java", UNFORMATTED);

    BuildResult result = fixture.build("stlFmt", "stlFmtCheck");

    assertThat(outcome(result, ":stlFmtCheck")).isEqualTo(TaskOutcome.UP_TO_DATE);
    assertThat(ranSpotless(result)).isFalse();
    assertThat(fixture.read("src/main/java/App.java")).isEqualTo(UNFORMATTED);
  }

  @Test
  void spotlessDoesntAttachToCheck() {
    BuildResult result = fixture("").file("src/main/java/App.java", UNFORMATTED).build("check");

    assertThat(ranSpotless(result)).isFalse();
  }

  @Test
  void generatedSourcesUnderBuildGeneratedAreNotFormatChecked() {
    BuildResult result =
        fixture("sourceSets.main { java.srcDir(\"build/generated/sources/openapi\") }\n")
            .file("src/main/java/App.java", FORMATTED)
            .file("build/generated/sources/openapi/Gen.java", "public class Gen {   }\n")
            .build("stlFmtCheck");

    assertThat(outcome(result, ":spotlessJavaCheck")).isEqualTo(TaskOutcome.SUCCESS);
  }
}
