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

/** A throwaway consumer build, run with the configuration cache and strict warnings. */
final class Fixture {
  private final Path dir;

  Fixture(Path dir) {
    this.dir = dir;
    file("settings.gradle.kts", "rootProject.name = \"consumer\"\n");
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
    return runner(args).build();
  }

  BuildResult buildAndFail(String... args) {
    return runner(args).buildAndFail();
  }

  /** A task's outcome, or null when the task wasn't in the build. */
  static TaskOutcome outcome(BuildResult result, String path) {
    BuildTask task = result.task(path);
    return task == null ? null : task.getOutcome();
  }

  private GradleRunner runner(String... args) {
    List<String> arguments = new ArrayList<>(List.of(args));
    arguments.addAll(
        List.of(
            "--configuration-cache", "--configuration-cache-problems=fail", "--warning-mode=fail"));
    return GradleRunner.create()
        .withProjectDir(dir.toFile())
        .withPluginClasspath()
        .withArguments(arguments);
  }
}
