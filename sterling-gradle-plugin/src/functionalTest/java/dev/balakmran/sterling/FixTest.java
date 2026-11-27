package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** stlFix: Error Prone's fixes applied to the sources, then formatted. */
class FixTest {
  private static final String APP =
      """
      package app;

      public class App {
        public String toString() {
          return "app";
        }
      }
      """;

  // Type-use annotations before the modifiers: AnnotationPosition's finding.
  private static final String MISPLACED =
      """
      package app;

      import org.jspecify.annotations.Nullable;

      public class Misplaced {
        @Nullable private String g;

        @Nullable
        public String f() {
          return g;
        }
      }
      """;

  private static final String DEPENDENCIES =
      """
      dependencies {
          compileOnly("org.projectlombok:lombok:1.18.48")
          annotationProcessor("org.projectlombok:lombok:1.18.48")
          implementation("org.mapstruct:mapstruct:1.6.3")
          annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
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
                + script)
        .file("src/main/java/app/App.java", APP);
  }

  @Test
  void stlFixInTheSameBuildAsVerificationFailsAtGraphTimeNamingTheConflict() {
    for (String verify : new String[] {"stlCheck", "stlLint", "stlFmtCheck", "check"}) {
      BuildResult result = fixture("").buildAndFail("stlFix", verify);

      assertThat(result.getOutput())
          .describedAs(verify)
          .contains(":stlFix changes sources, so it can't run in the same build as")
          .contains(":" + verify);
      // Before any task ran: nothing was compiled or patched.
      assertThat(outcome(result, ":compileJava")).describedAs(verify).isNull();
    }
  }

  // One construct per default patch check: each must be rewritten, and the result must be stable.
  private static final String EVERY_DEFAULT_CHECK =
      """
      package app;

      import org.jspecify.annotations.Nullable;

      public class Everything {
        @Nullable private String note;

        public String toString() {
          return "everything";
        }

        public boolean precedence(boolean a, boolean b, boolean c) {
          return a && b || c;
        }

        public int parentheses(int x) {
          int y = (x);
          return y;
        }

        public int pattern(Object o) {
          if (o instanceof String) {
            String s = (String) o;
            return s.length();
          }
          return 0;
        }

        /**
         * Doubles.
         *
         * @param x
         * @return
         */
        public int twice(int x) {
          return 2 * x;
        }
      }
      """;

  private static final String MISPLACED_PATH = "src/main/java/app/Misplaced.java";

  @Test
  void stlFixMovesTypeUseAnnotationsAfterTheModifiersAndASecondRunChangesNothing() {
    Fixture fixture = fixture("").file(MISPLACED_PATH, MISPLACED);

    BuildResult result = fixture.build("stlFix");
    String fixed = fixture.read(MISPLACED_PATH);

    assertThat(outcome(result, ":compileJava")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":spotlessJavaApply")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixed)
        .contains("private @Nullable String g;")
        .contains("public @Nullable String f() {")
        .doesNotContain("@Nullable private")
        .doesNotContain("@Nullable public");
    // Spotless ran after patching, so no stray whitespace is left for the format check.
    assertThat(outcome(fixture.build("stlFmtCheck"), ":spotlessJavaCheck"))
        .isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);

    fixture.build("stlFix");
    assertThat(fixture.read(MISPLACED_PATH)).isEqualTo(fixed);
  }

  @Test
  void stlFixPatchesTestSourcesToo() {
    Fixture fixture =
        fixture("")
            .file(
                "src/test/java/app/MisplacedTest.java",
                MISPLACED.replace("Misplaced", "MisplacedTest"));

    fixture.build("stlFix");

    assertThat(fixture.read("src/test/java/app/MisplacedTest.java"))
        .contains("private @Nullable String g;");
  }

  @Test
  void stlFixLeavesLombokMapStructAndGeneratedSourcesAlone() {
    String order =
        """
        package app;

        import lombok.Builder;
        import lombok.Value;

        @Value
        @Builder
        public class Order {
          String id;
          int quantity;
        }
        """;
    String mapper =
        """
        package app;

        import org.mapstruct.Mapper;

        @Mapper
        public interface OrderMapper {
          Order copy(Order order);
        }
        """;
    String generated =
        "package app;\n\npublic class Gen {\n  public String toString() { return \"g\"; }\n}\n";
    Fixture fixture =
        fixture(
                DEPENDENCIES
                    + "sourceSets.main { java.srcDir(\"build/generated/sources/openapi\") }\n")
            .file("src/main/java/app/Order.java", order)
            .file("src/main/java/app/OrderMapper.java", mapper)
            .file("build/generated/sources/openapi/app/Gen.java", generated)
            .file(MISPLACED_PATH, MISPLACED);

    BuildResult result = fixture.build("stlFix");

    assertThat(outcome(result, ":compileJava")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixture.read("src/main/java/app/Order.java")).isEqualTo(order);
    assertThat(fixture.read("src/main/java/app/OrderMapper.java")).isEqualTo(mapper);
    assertThat(fixture.read("build/generated/sources/openapi/app/Gen.java")).isEqualTo(generated);
    // The patch really ran: the one misplaced annotation in the project was moved.
    assertThat(fixture.read(MISPLACED_PATH)).contains("private @Nullable String g;");
  }

  @Test
  void aCompilationAfterStlFixNeitherPatchesNorReusesThePatchingRun() {
    Fixture fixture = fixture("").file(MISPLACED_PATH, MISPLACED);
    fixture.build("stlFix");
    fixture.file(MISPLACED_PATH, MISPLACED);

    BuildResult result = fixture.build("compileJava");

    assertThat(outcome(result, ":compileJava")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixture.read(MISPLACED_PATH)).isEqualTo(MISPLACED);
  }

  @Test
  void withErrorProneOffStlFixOnlyFormats() {
    Fixture fixture =
        fixture("sterling { errorProne { enabled = false } }\n")
            .file(
                MISPLACED_PATH,
                MISPLACED.replace("public class Misplaced {", "public class Misplaced {   "));

    BuildResult result = fixture.build("stlFix");

    assertThat(outcome(result, ":compileJava")).isNull();
    assertThat(outcome(result, ":spotlessJavaApply")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixture.read(MISPLACED_PATH))
        .contains("public class Misplaced {\n")
        .contains("@Nullable private String g;");
  }

  @Test
  void aSecondStlFixReusesTheConfigurationCacheAndStillPatches() {
    Fixture fixture = fixture("").file(MISPLACED_PATH, MISPLACED);
    fixture.build("stlFix");
    fixture.file(MISPLACED_PATH, MISPLACED);

    BuildResult second = fixture.build("stlFix");

    assertThat(second.getOutput()).contains("Reusing configuration cache.");
    assertThat(fixture.read(MISPLACED_PATH)).contains("private @Nullable String g;");
  }

  @Test
  void stlFixPatchesUnderStrictDespiteWerror() {
    Fixture fixture =
        fixture("sterling { profile = dev.balakmran.sterling.Profile.STRICT }\n")
            .file("src/main/java/app/App.java", APP);

    fixture.build("stlFix");

    assertThat(fixture.read("src/main/java/app/App.java")).contains("@Override");
  }

  @Test
  void aDisabledCheckIsLeftOutOfTheRunAndAnAddedOneJoinsIt() {
    Fixture fixture =
        fixture("sterling { errorProne { disable(\"MissingOverride\") } }\n")
            .file("src/main/java/app/App.java", APP)
            .file(MISPLACED_PATH, MISPLACED);

    fixture.build("stlFix");

    assertThat(fixture.read("src/main/java/app/App.java")).doesNotContain("@Override");
    assertThat(fixture.read(MISPLACED_PATH)).contains("private @Nullable String g;");
  }

  @Test
  void stlInfoJsonListsThePatchChecks() {
    String json = fixture("").build("-q", "stlInfo", "--json").getOutput();

    assertThat(json)
        .contains(
            "\"patchChecks\":[\"AnnotationPosition\",\"EmptyBlockTag\",\"MissingOverride\","
                + "\"OperatorPrecedence\",\"PatternMatchingInstanceof\","
                + "\"UnnecessaryParentheses\"]");
  }

  @Test
  void everyDefaultPatchCheckRewritesItsOwnFindingAndASecondRunChangesNothing() {
    Fixture fixture = fixture("").file("src/main/java/app/Everything.java", EVERY_DEFAULT_CHECK);

    fixture.build("stlFix");
    String fixed = fixture.read("src/main/java/app/Everything.java");

    assertThat(fixed)
        .describedAs("AnnotationPosition")
        .contains("private @Nullable String note;")
        .describedAs("MissingOverride")
        .contains("@Override\n  public String toString()")
        .describedAs("OperatorPrecedence")
        .contains("(a && b) || c")
        .describedAs("UnnecessaryParentheses")
        .contains("int y = x;")
        .describedAs("PatternMatchingInstanceof")
        .contains("o instanceof String s")
        .describedAs("EmptyBlockTag")
        .doesNotContain("@param x\n")
        .doesNotContain("@return\n");

    fixture.build("stlFix");
    assertThat(fixture.read("src/main/java/app/Everything.java")).isEqualTo(fixed);
  }
}
