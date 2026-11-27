package dev.balakmran.sterling.internal;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * Turns the source paths tools report into the paths Sterling prints.
 *
 * <p>SpotBugs and JaCoCo name a source file by its package-relative path, such as {@code
 * com/example/Foo.java}. Sterling prints where the file is, relative to the root project and with
 * {@code /} separators, so a CI matcher, an IDE or an agent can open it from the repository root.
 */
final class SourcePaths {
  private SourcePaths() {}

  /**
   * A lookup from a package-relative source path to the path to print.
   *
   * @param root the root project's directory
   * @param sourceDirectories the source directories to search, in order
   * @return the function; a path found in no directory is returned as it was
   */
  static Function<String, String> resolver(File root, List<File> sourceDirectories) {
    Path base = root.toPath();
    List<File> directories = List.copyOf(sourceDirectories);
    return sourcePath -> {
      for (File directory : directories) {
        File source = new File(directory, sourcePath);
        if (source.isFile()) {
          return relative(base, source);
        }
      }
      return sourcePath;
    };
  }

  /**
   * A file's path relative to a directory, or absolute when it's outside it.
   *
   * @param base the directory
   * @param file the file
   * @return the path, with {@code /} separators
   */
  static String relative(Path base, File file) {
    Path target = file.toPath();
    String shown = target.startsWith(base) ? base.relativize(target).toString() : target.toString();
    return shown.replace(File.separatorChar, '/');
  }
}
