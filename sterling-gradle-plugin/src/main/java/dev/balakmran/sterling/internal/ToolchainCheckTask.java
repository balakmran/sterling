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

/** Fails when the Java toolchain is below Sterling's floor. The marker keeps it up to date. */
@DisableCachingByDefault(because = "A version comparison; restoring from cache costs more.")
public abstract class ToolchainCheckTask extends DefaultTask {
  /** The minimum Java version for both the consumer toolchain and the Gradle daemon. */
  public static final int MIN_JAVA = 21;

  /** The language version of the compiler {@code compileJava} uses. */
  @Input
  public abstract Property<Integer> getLanguageVersion();

  /** Written on success, so the task stays up to date until the toolchain changes. */
  @OutputFile
  public abstract RegularFileProperty getMarker();

  /** Fails below the floor, otherwise writes the marker. */
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
