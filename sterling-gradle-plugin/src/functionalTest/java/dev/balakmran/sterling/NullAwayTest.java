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

  // Dereferences a @Nullable type argument: only NullAway's JSpecify mode sees it.
  private static final String NULLABLE_TYPE_ARGUMENT =
      """
      package generic;

      import org.jspecify.annotations.Nullable;

      public class Box<T extends @Nullable Object> {
        private final T value;

        public Box(T value) {
          this.value = value;
        }

        public T get() {
          return value;
        }

        public static int length(Box<@Nullable String> box) {
          return box.get().length();
        }
      }
      """;

  private static final String GENERIC_NULL_MARKED =
      """
      @NullMarked
      package generic;

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
                id("dev.balakmran.sterling")
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
  void disablingTheNullAwayCheckWhileNullAwayIsOffStillCompiles() {
    BuildResult result =
        fixture(
                """
                sterling {
                    errorProne { disable("NullAway") }
                    nullAway { enabled = false }
                }

                dependencies { compileOnly("org.jspecify:jspecify:1.0.0") }
                """)
            .file("src/main/java/app/package-info.java", NULL_MARKED)
            .build("compileJava");

    assertThat(outcome(result, COMPILE)).isEqualTo(TaskOutcome.SUCCESS);
  }

  private Fixture genericFixture(String script) {
    return fixture(script)
        .file("src/main/java/generic/Box.java", NULLABLE_TYPE_ARGUMENT)
        .file("src/main/java/generic/package-info.java", GENERIC_NULL_MARKED);
  }

  @Test
  void jspecifyModeIsOffUnderStandardSoTypeArgumentsAreNotChecked() {
    BuildResult result = genericFixture("").build("compileJava");

    assertThat(outcome(result, COMPILE)).isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void strictTurnsOnJspecifyModeWhichChecksTypeArguments() {
    BuildResult result =
        genericFixture("").buildAndFail("compileJava", "-Psterling.profile=strict");

    assertThat(outcome(result, COMPILE)).isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains("[NullAway]");
  }

  @Test
  void jspecifyModeSetExplicitlyWinsOverTheProfile() {
    BuildResult result =
        genericFixture("sterling { nullAway { jspecifyMode = true } }").buildAndFail("compileJava");

    assertThat(outcome(result, COMPILE)).isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains("[NullAway]");
  }

  @Test
  void disablingTheNullAwayCheckByNameStillTurnsItOff() {
    BuildResult result =
        fixture("sterling { errorProne { disable(\"NullAway\") } }")
            .file("src/main/java/app/package-info.java", NULL_MARKED)
            .build("compileJava");

    assertThat(outcome(result, COMPILE)).isEqualTo(TaskOutcome.SUCCESS);
  }

  private static final String SPRING_BOOT = "4.1.2";

  // Spring's Assert.notNull carries @Contract("null, _ -> fail"), so x is non-null after it.
  private static final String SPRING_ASSERT =
      """
      package app;

      import org.jspecify.annotations.Nullable;
      import org.springframework.util.Assert;

      public class Checked {
        public int length(@Nullable String x) {
          Assert.notNull(x, "x");
          return x.length();
        }
      }
      """;

  // Promises a non-null result for a non-null argument, but can return null. NullAway checks
  // only contracts that end in "!null"; it doesn't verify "-> fail".
  private static final String BROKEN_CONTRACT =
      """
      package app;

      import org.jspecify.annotations.Nullable;
      import org.springframework.lang.Contract;

      public class Broken {
        @Contract("!null -> !null")
        public static @Nullable String trim(@Nullable String value) {
          return value == null || value.isBlank() ? null : value.trim();
        }
      }
      """;

  private static final String ASSERTJ_TEST =
      """
      package app;

      import static org.assertj.core.api.Assertions.assertThat;

      import org.jspecify.annotations.Nullable;

      class LengthTest {
        static @Nullable String find() {
          return "x";
        }

        int length() {
          String x = find();
          assertThat(x).isNotNull();
          return x.length();
        }
      }
      """;

  // A project with Spring's spring-core and AssertJ, through Spring Boot's BOM, and no App.java.
  private Fixture springFixture(String script) {
    return new Fixture(dir)
        .file(
            "build.gradle.kts",
            """
            plugins {
                java
                id("dev.balakmran.sterling")
            }

            repositories { mavenCentral() }

            dependencies {
                implementation(platform("org.springframework.boot:spring-boot-dependencies:%s"))
                implementation("org.springframework:spring-core")
                testImplementation("org.assertj:assertj-core")
            }

            """
                    .formatted(SPRING_BOOT)
                + script)
        .file("src/main/java/app/package-info.java", NULL_MARKED)
        .file("src/test/java/app/package-info.java", NULL_MARKED);
  }

  @Test
  void springsAssertContractIsHonouredWithoutCustomContractAnnotations() {
    BuildResult result =
        springFixture("")
            .file("src/main/java/app/Checked.java", SPRING_ASSERT)
            .build("compileJava");

    assertThat(outcome(result, COMPILE)).isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void aMethodBodyThatBreaksItsContractFailsCompilation() {
    BuildResult result =
        springFixture("")
            .file("src/main/java/app/Broken.java", BROKEN_CONTRACT)
            .buildAndFail("compileJava");

    assertThat(outcome(result, COMPILE)).isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains("[NullAway]").contains("@Contract");
  }

  @Test
  void anAssertjNotNullAssertionCountsAsANullCheckInTests() {
    BuildResult result =
        springFixture("")
            .file("src/test/java/app/LengthTest.java", ASSERTJ_TEST)
            .build("compileTestJava");

    assertThat(outcome(result, ":compileTestJava")).isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void aProjectsOwnErrorProneOptionWinsOverSterlings() {
    BuildResult result =
        springFixture(
                """
                tasks.withType<JavaCompile>().configureEach {
                    (options as ExtensionAware).extensions
                        .getByType<net.ltgt.gradle.errorprone.ErrorProneOptions>()
                        .option("NullAway:CheckContracts", "false")
                }
                """)
            .file("src/main/java/app/Broken.java", BROKEN_CONTRACT)
            .build("compileJava");

    assertThat(outcome(result, COMPILE)).isEqualTo(TaskOutcome.SUCCESS);
  }
}
