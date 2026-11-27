package dev.balakmran.sterling.internal;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Counts a project's packages and those marked {@code @NullMarked}, for {@code stlInfo}.
 *
 * <p>A package is a directory under a source directory that holds a {@code .java} file, named by
 * its path from that source directory, so one split across source directories counts once. It's
 * {@code @NullMarked} when its {@code package-info.java} has {@code @NullMarked} or {@code
 * @org.jspecify.annotations.NullMarked} on the package declaration, in any source directory.
 * Classes marked one by one aren't counted.
 *
 * @param total the packages
 * @param nullMarked those marked {@code @NullMarked}
 */
record NullMarkedPackages(int total, int nullMarked) {
  private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);
  private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+[\\w.\\s]+;");
  private static final Pattern NULL_MARKED =
      Pattern.compile(
          "@\\s*(?:org\\s*\\.\\s*jspecify\\s*\\.\\s*annotations\\s*\\.\\s*)?NullMarked\\b");

  /**
   * Counts the packages under some source directories.
   *
   * @param sourceDirectories the source directories; ones that don't exist count for nothing
   * @return the counts
   */
  static NullMarkedPackages of(Collection<File> sourceDirectories) {
    Set<String> packages = new TreeSet<>();
    Set<String> marked = new TreeSet<>();
    for (File directory : sourceDirectories) {
      Path root = directory.toPath();
      if (!Files.isDirectory(root)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(root)) {
        files
            .filter(file -> file.toString().endsWith(".java") && Files.isRegularFile(file))
            .forEach(file -> {
              String name =
                  root.relativize(file).getParent() == null
                      ? ""
                      : root.relativize(file)
                          .getParent()
                          .toString()
                          .replace(File.separatorChar, '/');
              packages.add(name);
              if (file.getFileName().toString().equals("package-info.java") && markedIn(file)) {
                marked.add(name);
              }
            });
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return new NullMarkedPackages(
        packages.size(), (int) marked.stream().filter(packages::contains).count());
  }

  // Whether the annotations before the package declaration include @NullMarked.
  private static boolean markedIn(Path packageInfo) {
    try {
      String source =
          COMMENTS.matcher(Files.readString(packageInfo, StandardCharsets.UTF_8)).replaceAll(" ");
      Matcher declaration = PACKAGE.matcher(source);
      return declaration.find()
          && NULL_MARKED.matcher(source.substring(0, declaration.start())).find();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
