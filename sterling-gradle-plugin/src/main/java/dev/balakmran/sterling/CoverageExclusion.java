package dev.balakmran.sterling;

import dev.balakmran.sterling.internal.GeneratedClassesTask;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;

/**
 * Class files Sterling always leaves out of coverage: those compiled from generated sources.
 *
 * <p>The list is only known once {@code stlGeneratedClasses} has run, so it's read when JaCoCo
 * walks the class directories, not when the task is configured. A class's nested and anonymous
 * classes ({@code Name$Inner.class}, {@code Name$1.class}) go with it.
 *
 * <p>It's a class holding only a {@link File}, rather than a lambda, so the configuration cache can
 * store it. The names read from the file aren't stored; they're read again when the file changes.
 */
final class CoverageExclusion implements Spec<FileTreeElement> {
  private final File generatedClasses;

  // Not stored by the configuration cache: re-read after a cache hit.
  private transient Set<String> names;
  private transient long namesStamp;

  /**
   * Creates the exclusion.
   *
   * @param generatedClasses the list {@code stlGeneratedClasses} writes; missing reads as empty
   */
  CoverageExclusion(File generatedClasses) {
    this.generatedClasses = generatedClasses;
  }

  /**
   * Whether a class file is left out.
   *
   * @param element a file or directory under a classes directory
   * @return true for a class file of a generated class or one nested in it
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
    long stamp = generatedClasses.lastModified();
    if (names == null || stamp != namesStamp) {
      Set<String> paths = new HashSet<>();
      for (String name : GeneratedClassesTask.read(generatedClasses)) {
        paths.add(name.replace('.', '/'));
      }
      names = paths;
      namesStamp = stamp;
    }
    return names;
  }
}
