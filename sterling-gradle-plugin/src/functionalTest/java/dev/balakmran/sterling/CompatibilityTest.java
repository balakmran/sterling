package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behaviour across the tools: generated code, direct tool configuration, caching. */
class CompatibilityTest {
  // Clean by every tool's standard, and fully covered by its test.
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

            testing { suites { named<JvmTestSuite>("test") { useJUnitJupiter() } } }

            """
                + script)
        .file("src/main/java/app/Calc.java", CALC)
        .file("src/test/java/app/CalcTest.java", CALC_TEST);
  }

  @Test
  void generatedSourcesTripNoTool() {
    // Unformatted, with an Error Prone error, a NullAway error and a SpotBugs bug.
    String generated =
        """
        package gen;
        import org.jspecify.annotations.Nullable;
        public class Api {   static @Nullable String find() { return null; }
          public int run() { new IllegalStateException("x"); String text = null;
            return text.length() + find().length(); }
          public static class Nested { public int size() { String s = null; return s.length(); } } }
        """;
    BuildResult result =
        fixture("sourceSets.main { java.srcDir(\"build/generated/sources/openapi\") }\n")
            .file("build/generated/sources/openapi/gen/Api.java", generated)
            .file(
                "build/generated/sources/openapi/gen/package-info.java",
                "@org.jspecify.annotations.NullMarked\npackage gen;\n")
            .build("stlFmtCheck", "stlLint", "stlSec");

    List.of(":spotlessJavaCheck", ":compileJava", ":spotbugsMain")
        .forEach(task ->
            assertThat(outcome(result, task)).describedAs(task).isEqualTo(TaskOutcome.SUCCESS));
    assertThat(new Fixture(dir).read("build/sterling/spotbugs-exclude.xml"))
        .contains("~gen\\.Api(\\$.*)?");
  }

  @Test
  void aProjectsOwnErrorProneSettingsWin() {
    String missingOverride =
        """
        package app;

        public class Named {
          public String toString() {
            return "named";
          }
        }
        """;
    Fixture fixture =
        fixture(
                """
                tasks.withType<JavaCompile>().configureEach {
                    options.errorprone.error("MissingOverride")
                }
                """)
            .file("src/main/java/app/Named.java", missingOverride);
    // build.gradle.kts needs the Kotlin extension's import to use options.errorprone.
    fixture.file(
        "build.gradle.kts",
        "import net.ltgt.gradle.errorprone.errorprone\n\n" + fixture.read("build.gradle.kts"));

    BuildResult result = fixture.buildAndFail("compileJava");

    assertThat(outcome(result, ":compileJava")).isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains("[MissingOverride]");

    fixture.file(
        "build.gradle.kts",
        fixture
            .read("build.gradle.kts")
            .replace(
                "options.errorprone.error(\"MissingOverride\")",
                "options.errorprone.enabled = false"));
    assertThat(outcome(fixture.build("compileJava"), ":compileJava"))
        .isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void aSecondRunWithUnchangedSourcesDoesNoWork() {
    Fixture fixture = fixture("");
    fixture.build("stlCheck");

    BuildResult second = fixture.build("stlCheck");

    List.of(
            ":stlToolchainCheck",
            ":spotlessJava",
            ":compileJava",
            ":compileTestJava",
            ":test",
            ":stlSpotbugsFilter",
            ":spotbugsMain",
            ":jacocoTestCoverageVerification")
        .forEach(task ->
            assertThat(outcome(second, task))
                .describedAs(task)
                .isIn(TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE));
  }

  @Test
  void aSecondRunReusesTheConfigurationCache() {
    Fixture fixture = fixture("");
    BuildResult first = fixture.build("stlCheck");
    assertThat(first.getOutput()).contains("Configuration cache entry stored.");

    BuildResult second = fixture.build("stlCheck");

    assertThat(second.getOutput())
        .contains("Reusing configuration cache.")
        .doesNotContain("problems were found")
        .doesNotContain("problem was found");
  }
}
