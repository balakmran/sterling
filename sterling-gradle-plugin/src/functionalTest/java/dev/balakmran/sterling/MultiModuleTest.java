package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The multi-module recipe: a build-logic convention plugin that applies Sterling. */
class MultiModuleTest {
  @TempDir Path dir;

  @Test
  void aConventionPluginGatesEveryModuleAndAModuleCanOverrideIt() {
    Fixture fixture =
        new Fixture(dir)
            .file(
                "settings.gradle.kts",
                """
                pluginManagement { includeBuild("build-logic") }
                rootProject.name = "consumer"
                include("a", "b")
                """)
            .file("build-logic/settings.gradle.kts", "rootProject.name = \"build-logic\"\n")
            .file(
                "build-logic/build.gradle.kts",
                """
                plugins { `kotlin-dsl` }

                repositories { gradlePluginPortal() }

                // build-logic runs in the Gradle daemon; the fixture's init script would otherwise
                // give it the consumer toolchain, which can be newer than the daemon's JDK.
                java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

                // Real builds depend on dev.balakmran.sterling:sterling-gradle-plugin.
                dependencies { implementation(files(%s)) }
                """
                    .formatted(sterlingClasspath()))
            .file(
                "build-logic/src/main/kotlin/java-conventions.gradle.kts",
                """
                plugins {
                    java
                    id("dev.balakmran.sterling")
                }

                repositories { mavenCentral() }
                """)
            .file("a/build.gradle.kts", "plugins { id(\"java-conventions\") }\n")
            .file(
                "b/build.gradle.kts",
                """
                plugins { id("java-conventions") }

                sterling { coverage { instruction = 50 } }
                """);

    BuildResult check = fixture.build("stlCheck");

    // Each module's gate ran from the root: its toolchain check executes, so it isn't up to date.
    assertThat(outcome(check, ":a:stlCheck")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(check, ":b:stlCheck")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixture.build("-q", ":a:stlInfo").getOutput())
        .contains("instruction >= 80%, branch >= 70%");
    assertThat(fixture.build("-q", ":b:stlInfo").getOutput())
        .contains("instruction >= 50%, branch >= 70%");
  }

  // TestKit's classpath for the plugin under test, as Kotlin string literals. TestKit injects it
  // into the root build only, so build-logic gets it as a file dependency.
  private static String sterlingClasspath() {
    Properties metadata = new Properties();
    try (InputStream in =
        MultiModuleTest.class
            .getClassLoader()
            .getResourceAsStream("plugin-under-test-metadata.properties")) {
      assertThat(in).describedAs("plugin-under-test-metadata.properties").isNotNull();
      metadata.load(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return Arrays.stream(metadata.getProperty("implementation-classpath").split(File.pathSeparator))
        .map(path -> "\"" + path.replace('\\', '/') + "\"")
        .collect(Collectors.joining(", "));
  }
}
