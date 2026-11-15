package dev.balakmran.sterling;

import dev.balakmran.sterling.internal.GeneratedClassesTask;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;

/**
 * Class files Sterling always leaves out of coverage: those compiled from generated sources, and
 * with Spring Boot, the application's main class.
 *
 * <p>Both are only known once a task has run ({@code stlGeneratedClasses}, Spring Boot's {@code
 * resolveMainClassName}), so their files are read when JaCoCo walks the class directories, not when
 * the task is configured. A class's nested and anonymous classes ({@code Name$Inner.class}, {@code
 * Name$1.class}) go with it.
 *
 * <p>It's a class holding only {@link File}s, rather than a lambda, so the configuration cache can
 * store it. The names read from the files aren't stored; they're read again when a file changes.
 */
final class CoverageExclusion implements Spec<FileTreeElement> {
  private final File generatedClasses;
  private final File mainClassName;

  // Not stored by the configuration cache: re-read after a cache hit.
  private transient Set<String> names;
  private transient List<Long> namesStamp;

  /**
   * Creates the exclusion.
   *
   * @param generatedClasses the list {@code stlGeneratedClasses} writes; missing reads as empty
   * @param mainClassName the file {@code resolveMainClassName} writes, holding one class name, or
   *     null without Spring Boot; missing or blank means no main class
   */
  CoverageExclusion(File generatedClasses, File mainClassName) {
    this.generatedClasses = generatedClasses;
    this.mainClassName = mainClassName;
  }

  /**
   * Whether a class file is left out.
   *
   * @param element a file or directory under a classes directory
   * @return true for a class file of an excluded class or one nested in it
   */
  @Override
  public boolean isSatisfiedBy(FileTreeElement element) {
    if (element.isDirectory()) {
      return false;
    }
    String path = element.getRelativePath().getPathString();
    if (!path.endsWith(".class")) {
      return false;
    }
    return excluded().contains(outerClass(path.substring(0, path.length() - ".class".length())));
  }

  // com/example/Foo$Bar$1 -> com/example/Foo: nested classes go with the class they're in.
  private static String outerClass(String path) {
    int simpleName = path.lastIndexOf('/') + 1;
    int nested = path.indexOf('$', simpleName);
    return nested < 0 ? path : path.substring(0, nested);
  }

  private synchronized Set<String> excluded() {
    List<Long> stamp =
        List.of(
            generatedClasses.lastModified(),
            mainClassName == null ? 0L : mainClassName.lastModified());
    if (names == null || !stamp.equals(namesStamp)) {
      Set<String> paths = new HashSet<>();
      for (String name : GeneratedClassesTask.read(generatedClasses)) {
        paths.add(asPath(name));
      }
      String main = readMainClassName();
      if (!main.isEmpty()) {
        paths.add(asPath(main));
      }
      names = paths;
      namesStamp = stamp;
    }
    return names;
  }

  private String readMainClassName() {
    if (mainClassName == null || !mainClassName.isFile()) {
      return "";
    }
    try {
      return Files.readString(mainClassName.toPath(), StandardCharsets.UTF_8).strip();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // A binary name, com.example.Foo, as a class file path without the extension.
  private static String asPath(String className) {
    return className.replace('.', '/');
  }
}
