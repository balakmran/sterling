package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** NullAway inside Error Prone, for {@code @NullMarked} code, behind the nullAway flag. */
class NullAwayTest {
  private static final String COMPILE = ":compileJava";

  // Dereferences a value declared @Nullable without a check.
  private static final String UNCHECKED_NULLABLE =
      """
      package app;

      import org.jspecify.annotations.Nullable;

      public class App {
        static @Nullable String find() {
          return null;
        }

        public int length() {
          return find().length();
        }
      }
      """;

  private static final String NULL_MARKED =
      """
      @NullMarked
      package app;

      import org.jspecify.annotations.NullMarked;
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
                + script)
        .file("src/main/java/app/App.java", UNCHECKED_NULLABLE);
  }

  private static String version(String tool) {
    return System.getProperty("sterling." + tool + "Version");
  }

  @Test
  void aNullnessBugInNullMarkedCodeFailsCompilationWithoutAJspecifyDependency() {
    BuildResult result =
        fixture("")
            .file("src/main/java/app/package-info.java", NULL_MARKED)
            .buildAndFail("compileJava");

    assertThat(outcome(result, COMPILE)).isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains("[NullAway]");
  }

  @Test
  void codeOutsideNullMarkedPackagesIsNotChecked() {
    BuildResult result = fixture("").build("compileJava");

    assertThat(outcome(result, COMPILE)).isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void nullAwayDisabledLeavesTheProjectsOwnJspecifyAlone() {
    Fixture fixture =
        fixture(
                """
                sterling { nullAway { enabled = false } }

                dependencies { compileOnly("org.jspecify:jspecify:1.0.0") }
                """)
            .file("src/main/java/app/package-info.java", NULL_MARKED);

    assertThat(outcome(fixture.build("compileJava"), COMPILE)).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixture.build("dependencies", "--configuration", "compileClasspath").getOutput())
        .contains("org.jspecify:jspecify:1.0.0\n")
        .doesNotContain("jspecify:1.0.0 -> ");
    assertThat(fixture.build("dependencies", "--configuration", "annotationProcessor").getOutput())
        .doesNotContain("nullaway");
  }

  @Test
  void nullAwayEnabledAddsTheJarAndCompileOnlyJspecify() {
    Fixture fixture = fixture("");

    assertThat(fixture.build("dependencies", "--configuration", "annotationProcessor").getOutput())
        .contains("com.uber.nullaway:nullaway:" + version("nullaway"));
    assertThat(fixture.build("dependencies", "--configuration", "compileClasspath").getOutput())
        .contains("org.jspecify:jspecify:" + version("jspecify"));
    assertThat(fixture.build("dependencies", "--configuration", "testCompileClasspath").getOutput())
        .contains("org.jspecify:jspecify:" + version("jspecify"));
    assertThat(fixture.build("dependencies", "--configuration", "runtimeClasspath").getOutput())
        .doesNotContain("jspecify");
  }

  @Test
  void disablingTheNullAwayCheckByNameStillTurnsItOff() {
    BuildResult result =
        fixture("sterling { errorProne { disable(\"NullAway\") } }")
            .file("src/main/java/app/package-info.java", NULL_MARKED)
            .build("compileJava");

    assertThat(outcome(result, COMPILE)).isEqualTo(TaskOutcome.SUCCESS);
  }
}
