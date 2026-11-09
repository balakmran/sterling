package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Java plugin: activated by java, with a JDK 21 floor for the toolchain and daemon. */
class JavaPluginTest {
  private static final String SOURCE = "public class App {}\n";
  private static final String JAVA_AND_STERLING =
      """
      plugins {
          java
          id("dev.balakmran.sterling.java")
      }
      """;

  @TempDir Path dir;

  @Test
  void withoutJavaTheBuildSucceedsAndStlInfoReportsJavaNotActive() {
    BuildResult result =
        new Fixture(dir)
            .file("build.gradle.kts", "plugins { id(\"dev.balakmran.sterling.java\") }\n")
            .build("stlInfo", "stlCheck");

    assertThat(outcome(result, ":stlInfo")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":stlCheck")).isEqualTo(TaskOutcome.UP_TO_DATE);
    assertThat(result.getOutput())
        .contains("Java toolchain:       not active (java plugin not applied)");
  }

  @Test
  void toolchainOnTheFloorPassesTheCheckThenStaysUpToDate() {
    Fixture fixture =
        new Fixture(dir)
            .file("build.gradle.kts", JAVA_AND_STERLING)
            .file("src/main/java/App.java", SOURCE);

    BuildResult first = fixture.build("compileJava", "stlInfo");
    assertThat(outcome(first, ":stlToolchainCheck")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(first, ":compileJava")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(first.getOutput())
        .contains("Java toolchain:       " + Runtime.version().feature() + "\n");

    BuildResult second = fixture.build("compileJava");
    assertThat(outcome(second, ":stlToolchainCheck")).isEqualTo(TaskOutcome.UP_TO_DATE);
  }

  @Test
  void aJava17ToolchainFailsStlToolchainCheck() {
    BuildResult result =
        new Fixture(dir)
            .file(
                "gradle.properties",
                "org.gradle.java.installations.paths="
                    + Fixture.jdk(17)
                    + "\norg.gradle.java.installations.auto-detect=false"
                    + "\norg.gradle.java.installations.auto-download=false\n")
            .file(
                "build.gradle.kts",
                JAVA_AND_STERLING
                    + "\njava { toolchain { languageVersion = JavaLanguageVersion.of(17) } }\n")
            .file("src/main/java/App.java", SOURCE)
            .buildAndFail("compileJava");

    assertThat(outcome(result, ":stlToolchainCheck")).isEqualTo(TaskOutcome.FAILED);
    assertThat(outcome(result, ":compileJava")).isNull();
    assertThat(result.getOutput())
        .contains(
            "Sterling requires a Java 21+ toolchain (found 17). "
                + "Set java.toolchain.languageVersion to 21 or later.");
  }

  @Test
  void aGradleDaemonOnJdk17FailsAtConfiguration() {
    BuildResult result =
        new Fixture(dir)
            .file("gradle.properties", "org.gradle.java.home=" + Fixture.jdk(17) + "\n")
            .file("build.gradle.kts", "plugins { id(\"dev.balakmran.sterling.java\") }\n")
            .buildAndFail("help");

    assertThat(outcome(result, ":help")).isNull();
    assertThat(result.getOutput())
        .contains(
            "Sterling requires Gradle to run on JDK 21+ (found 17). "
                + "Run ./gradlew updateDaemonJvm --jvm-version=21, or point JAVA_HOME at JDK 21+.");
  }

  @Test
  void appliedBeforeJavaFromSubprojectsRootStlCheckRunsInBothModules() {
    BuildResult result =
        new Fixture(dir)
            .file(
                "settings.gradle.kts",
                """
                rootProject.name = "consumer"
                include("a", "b")
                """)
            .file(
                "build.gradle.kts",
                """
                plugins { id("dev.balakmran.sterling.java") apply false }

                subprojects {
                    apply(plugin = "dev.balakmran.sterling.java")
                    apply(plugin = "java")
                    repositories { mavenCentral() }
                }
                """)
            .file("a/src/main/java/App.java", SOURCE)
            .file("b/src/main/java/App.java", SOURCE)
            .build("stlCheck", "compileJava");

    List.of("a", "b")
        .forEach(module -> {
          assertThat(outcome(result, ":" + module + ":stlCheck")).describedAs(module).isNotNull();
          assertThat(outcome(result, ":" + module + ":stlToolchainCheck"))
              .describedAs(module)
              .isEqualTo(TaskOutcome.SUCCESS);
        });
  }
}
