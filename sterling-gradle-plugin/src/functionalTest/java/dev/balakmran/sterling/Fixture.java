package dev.balakmran.sterling;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;

/**
 * A throwaway consumer build, run with the configuration cache and strict deprecation checks.
 *
 * <p>Every build runs with {@code --warning-mode=all}, and any Gradle deprecation warning in the
 * output fails the test, except those in {@link #KNOWN_DEPRECATIONS}. That's {@code
 * --warning-mode=fail} with exceptions for third-party plugins Sterling can't fix.
 *
 * <p>Builds run on the matrix cell the {@code functionalTest} task passes in: a Gradle version
 * ({@link #gradleVersion()}) and a Java toolchain ({@link #javaVersion()}), which an init script
 * sets on every project that applies {@code java}. A fixture's own toolchain setting still wins.
 * Consumer builds only see the JDKs the task provides; they never detect or download others.
 */
final class Fixture {
  /**
   * Deprecation warnings from third-party plugins, matched by prefix. Remove each one once a
   * release of that plugin fixes it.
   */
  private static final List<String> KNOWN_DEPRECATIONS =
      List.of(
          // SpotBugs Gradle plugin 6.5.13 (the latest) calls it; Gradle 9.9 deprecates it.
          "The Configuration.setVisible(boolean) method has been deprecated.");

  private static final String INIT_SCRIPT = ".sterling-toolchain.init.gradle";

  private final Path dir;

  Fixture(Path dir) {
    this.dir = dir;
    file("settings.gradle.kts", "rootProject.name = \"consumer\"\n");
    file(
        INIT_SCRIPT,
        """
        gradle.beforeProject { project ->
            project.plugins.withId('java') {
                project.extensions.getByType(JavaPluginExtension).toolchain
                    .languageVersion.set(JavaLanguageVersion.of(%s))
            }
        }
        """
            .formatted(javaVersion()));
  }

  /**
   * The Gradle version consumer builds run on.
   *
   * @return a version such as {@code 9.0.0}, or blank for the Gradle running the tests
   */
  static String gradleVersion() {
    return System.getProperty("sterling.gradleVersion", "");
  }

  /**
   * The Java toolchain version consumer builds use, such as {@code 21} or {@code 25}.
   *
   * @return the version
   */
  static int javaVersion() {
    return Integer.parseInt(System.getProperty("sterling.javaVersion", "21"));
  }

  Fixture file(String path, String content) {
    Path target = dir.resolve(path);
    try {
      Files.createDirectories(target.getParent());
      Files.writeString(target, content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return this;
  }

  String read(String path) {
    try {
      return Files.readString(dir.resolve(path));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A JDK home from the functionalTest task, with {@code /} separators for gradle.properties. */
  static String jdk(int version) {
    String home = System.getProperty("sterling.jdk" + version);
    if (home == null) {
      throw new IllegalStateException("sterling.jdk" + version + " not set");
    }
    return home.replace('\\', '/');
  }

  BuildResult build(String... args) {
    return checkDeprecations(runner(args).build());
  }

  BuildResult buildAndFail(String... args) {
    return checkDeprecations(runner(args).buildAndFail());
  }

  /** A task's outcome, or null when the task wasn't in the build. */
  static TaskOutcome outcome(BuildResult result, String path) {
    BuildTask task = result.task(path);
    return task == null ? null : task.getOutcome();
  }

  private static BuildResult checkDeprecations(BuildResult result) {
    List<String> unexpected =
        result
            .getOutput()
            .lines()
            .filter(line -> line.contains("has been deprecated"))
            .filter(line -> KNOWN_DEPRECATIONS.stream().noneMatch(line::startsWith))
            .toList();
    if (!unexpected.isEmpty()) {
      throw new AssertionError(
          "Unexpected Gradle deprecations:\n"
              + String.join("\n", unexpected)
              + "\n\nBuild output:\n"
              + result.getOutput());
    }
    return result;
  }

  private GradleRunner runner(String... args) {
    List<String> arguments = new ArrayList<>(List.of(args));
    arguments.addAll(
        List.of(
            "--configuration-cache",
            "--configuration-cache-problems=fail",
            "--warning-mode=all",
            "--init-script=" + INIT_SCRIPT,
            "-Dorg.gradle.java.installations.paths=" + String.join(",", jdk(17), jdk(21), jdk(25)),
            "-Dorg.gradle.java.installations.auto-detect=false",
            "-Dorg.gradle.java.installations.auto-download=false"));
    GradleRunner runner =
        GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withPluginClasspath()
            .withArguments(arguments);
    return gradleVersion().isBlank() ? runner : runner.withGradleVersion(gradleVersion());
  }
}
