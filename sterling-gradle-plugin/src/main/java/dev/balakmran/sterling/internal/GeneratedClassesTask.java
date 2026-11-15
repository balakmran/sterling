package dev.balakmran.sterling.internal;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * The {@code stlGeneratedClasses} task: lists the classes compiled from generated sources.
 *
 * <p>Class files don't record which directory their source came from, so neither SpotBugs nor
 * JaCoCo can leave out {@code build/generated} by path. Instead this task reads the generated
 * source directories, after {@code compileJava} has run the annotation processors, and turns each
 * {@code .java} file's path into a binary class name. It writes one name per line, sorted, such as
 * {@code com.example.api.OrderDto}. {@code stlSpotbugsFilter} and the coverage tasks read the list,
 * so both tools agree on what's generated.
 */
@CacheableTask
public abstract class GeneratedClassesTask extends DefaultTask {
  /**
   * Creates the task. Sterling registers it as {@code stlGeneratedClasses}; Gradle instantiates it.
   */
  public GeneratedClassesTask() {}

  /**
   * Directories of generated Java sources. Missing ones are ignored.
   *
   * @return the generated source directories
   */
  @InputFiles
  @IgnoreEmptyDirectories
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract ConfigurableFileCollection getGeneratedSources();

  /**
   * Where the list is written.
   *
   * @return the list file
   */
  @OutputFile
  public abstract RegularFileProperty getClassList();

  /** Writes the list. */
  @TaskAction
  public void write() {
    List<String> names = generatedClasses(getGeneratedSources().getFiles());
    StringBuilder text = new StringBuilder();
    names.forEach(name -> text.append(name).append('\n'));
    try {
      Files.writeString(getClassList().get().getAsFile().toPath(), text, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The classes declared by the {@code .java} files under some source directories, named from each
   * file's path relative to its directory. {@code package-info.java} and {@code module-info.java}
   * are skipped.
   *
   * @param sourceDirectories source directories; ones that don't exist are skipped
   * @return the fully qualified class names, sorted
   */
  public static List<String> generatedClasses(Set<File> sourceDirectories) {
    List<String> names = new ArrayList<>();
    for (File directory : sourceDirectories) {
      Path root = directory.toPath();
      if (!Files.isDirectory(root)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(root)) {
        files
            .filter(file -> file.getFileName().toString().endsWith(".java"))
            .map(file -> root.relativize(file).toString())
            .filter(path -> !path.endsWith("package-info.java"))
            .filter(path -> !path.endsWith("module-info.java"))
            .map(path -> path.substring(0, path.length() - ".java".length()))
            .map(path -> path.replace(File.separatorChar, '.'))
            .forEach(names::add);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    names.sort(null);
    return names;
  }

  /**
   * Reads a list this task wrote.
   *
   * @param list the list file; a missing file reads as empty
   * @return the class names, in file order
   */
  public static List<String> read(File list) {
    if (!list.isFile()) {
      return List.of();
    }
    try {
      return Files.readAllLines(list.toPath(), StandardCharsets.UTF_8).stream()
          .map(String::strip)
          .filter(line -> !line.isEmpty())
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
