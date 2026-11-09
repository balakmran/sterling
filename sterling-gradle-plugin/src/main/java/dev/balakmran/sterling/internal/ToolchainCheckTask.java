package dev.balakmran.sterling.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * The {@code stlToolchainCheck} task: fails when the Java toolchain is below Java 21.
 *
 * <p>{@code compileJava} depends on it, so a project on an older toolchain gets Sterling's message
 * before Error Prone fails in a less obvious way. On success it writes a small marker file, which
 * keeps it up to date until the toolchain changes.
 */
@DisableCachingByDefault(because = "A version comparison; restoring from cache costs more.")
public abstract class ToolchainCheckTask extends DefaultTask {
  /**
   * Creates the task. Sterling registers it as {@code stlToolchainCheck}; Gradle instantiates it.
   */
  public ToolchainCheckTask() {}

  /** The minimum Java version for both the project's toolchain and the Gradle daemon. */
  public static final int MIN_JAVA = 21;

  /**
   * The language version of the compiler {@code compileJava} uses.
   *
   * @return the toolchain's language version
   */
  @Input
  public abstract Property<Integer> getLanguageVersion();

  /**
   * The marker file, written on success.
   *
   * @return the marker file
   */
  @OutputFile
  public abstract RegularFileProperty getMarker();

  /**
   * Fails below Java 21; otherwise writes the marker.
   *
   * @throws org.gradle.api.GradleException if the toolchain is below Java 21
   */
  @TaskAction
  public void check() {
    int version = getLanguageVersion().get();
    if (version < MIN_JAVA) {
      throw new GradleException(
          "Sterling requires a Java "
              + MIN_JAVA
              + "+ toolchain (found "
              + version
              + "). Set java.toolchain.languageVersion to "
              + MIN_JAVA
              + " or later.");
    }
    try {
      Files.writeString(getMarker().get().getAsFile().toPath(), version + "\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
