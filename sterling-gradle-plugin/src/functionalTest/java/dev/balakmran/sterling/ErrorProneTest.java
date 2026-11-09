package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Error Prone inside compilation, run by stlLint behind the errorProne flag. */
class ErrorProneTest {
  private static final String COMPILE = ":compileJava";

  // An exception created but never thrown: DeadException, an error-level check.
  private static final String DEAD_EXCEPTION =
      """
      package app;

      public class App {
        public void run() {
          new IllegalStateException("never thrown");
        }
      }
      """;

  // toString() without @Override: MissingOverride, a warning-level check.
  private static final String MISSING_OVERRIDE =
      """
      package app;

      public class App {
        public String toString() {
          return "app";
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

            """
                + sterling);
  }

  @Test
  void anErrorLevelFindingFailsCompilation() {
    BuildResult result =
        fixture("").file("src/main/java/app/App.java", DEAD_EXCEPTION).buildAndFail("stlLint");

    assertThat(outcome(result, COMPILE)).isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains("[DeadException]");
  }

  @Test
  void errorProneDisabledDownloadsNeitherItNorNullAwayNorJspecify() {
    Fixture fixture =
        fixture("sterling { errorProne { enabled = false } }")
            .file("src/main/java/app/App.java", DEAD_EXCEPTION);

    assertThat(outcome(fixture.build("compileJava"), COMPILE)).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixture.build("dependencies", "--configuration", "annotationProcessor").getOutput())
        .doesNotContain("error_prone_core")
        .doesNotContain("nullaway");
    assertThat(fixture.build("dependencies", "--configuration", "compileClasspath").getOutput())
        .doesNotContain("jspecify");
  }

  @Test
  void errorProneEnabledDeclaresThePinnedCore() {
    BuildResult result =
        fixture("").build("dependencies", "--configuration", "annotationProcessor");

    assertThat(result.getOutput())
        .contains(
            "com.google.errorprone:error_prone_core:"
                + System.getProperty("sterling.errorproneVersion"));
  }

  @Test
  void warningsFailCompilationOnlyWhileWarningsAsErrorsIsOn() {
    Fixture fixture =
        fixture("sterling { errorProne { warningsAsErrors = true } }")
            .file("src/main/java/app/App.java", MISSING_OVERRIDE);
    BuildResult strict = fixture.buildAndFail("compileJava");
    assertThat(outcome(strict, COMPILE)).isEqualTo(TaskOutcome.FAILED);
    assertThat(strict.getOutput()).contains("[MissingOverride]");

    fixture.file("build.gradle.kts", fixture.read("build.gradle.kts").replace("= true", "= false"));
    assertThat(outcome(fixture.build("compileJava"), COMPILE)).isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void aDisabledCheckNoLongerFailsCompilation() {
    BuildResult result =
        fixture("sterling { errorProne { disable(\"DeadException\") } }")
            .file("src/main/java/app/App.java", DEAD_EXCEPTION)
            .build("compileJava");

    assertThat(outcome(result, COMPILE)).isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void generatedSourcesUnderBuildGeneratedAreSkipped() {
    BuildResult result =
        fixture("sourceSets.main { java.srcDir(\"build/generated/sources/openapi\") }\n")
            .file("build/generated/sources/openapi/app/App.java", DEAD_EXCEPTION)
            .build("compileJava");

    assertThat(outcome(result, COMPILE)).isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void stlLintCompilesMainAndTestSources() {
    BuildResult result =
        fixture("")
            .file("src/main/java/app/App.java", "package app;\n\npublic class App {}\n")
            .build("stlLint");

    assertThat(outcome(result, COMPILE)).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":compileTestJava")).isNotNull();
  }
}
