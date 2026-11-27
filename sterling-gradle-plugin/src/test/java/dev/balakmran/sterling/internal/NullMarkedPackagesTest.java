package dev.balakmran.sterling.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NullMarkedPackagesTest {
  @TempDir Path dir;

  private Path java(String relative, String content) throws IOException {
    Path file = dir.resolve(relative);
    Files.createDirectories(file.getParent());
    return Files.writeString(file, content);
  }

  private NullMarkedPackages count(String... sourceDirectories) {
    return NullMarkedPackages.of(
        java.util.Arrays.stream(sourceDirectories)
            .map(name -> dir.resolve(name).toFile())
            .toList());
  }

  @Test
  void countsPackagesAndThoseMarkedNullMarkedByEitherName() throws IOException {
    java("src/a/Root.java", "class Root {}\n");
    java("src/a/one/A.java", "package one;\nclass A {}\n");
    java(
        "src/a/one/package-info.java",
        "@NullMarked\npackage one;\n\nimport org.jspecify.annotations.NullMarked;\n");
    java("src/a/two/B.java", "package two;\nclass B {}\n");
    java("src/a/two/package-info.java", "@org.jspecify.annotations.NullMarked\npackage two;\n");
    java("src/a/three/C.java", "package three;\nclass C {}\n");
    java("src/a/four/D.java", "package four;\nclass D {}\n");
    java("src/a/four/package-info.java", "package four;\n");
    java("src/a/empty/readme.txt", "no java here\n");

    assertThat(count("src/a")).isEqualTo(new NullMarkedPackages(5, 2));
  }

  @Test
  void aNullMarkedInACommentOrAfterThePackageDeclarationDoesntCount() throws IOException {
    java("src/a/one/A.java", "package one;\nclass A {}\n");
    java("src/a/one/package-info.java", "// @NullMarked\n/* @NullMarked */\npackage one;\n");
    java("src/a/two/B.java", "package two;\nclass B {}\n");
    java("src/a/two/package-info.java", "package two;\n// @NullMarked\n");

    assertThat(count("src/a")).isEqualTo(new NullMarkedPackages(2, 0));
  }

  @Test
  void aPackageSplitAcrossSourceDirectoriesCountsOnceAndIsMarkedByEitherPackageInfo()
      throws IOException {
    java("src/a/one/A.java", "package one;\nclass A {}\n");
    java("src/b/one/B.java", "package one;\nclass B {}\n");
    java("src/b/one/package-info.java", "@NullMarked\npackage one;\n");

    assertThat(count("src/a", "src/b")).isEqualTo(new NullMarkedPackages(1, 1));
  }

  @Test
  void aMissingSourceDirectoryCountsForNothing() {
    assertThat(NullMarkedPackages.of(List.of(new File(dir.toFile(), "nope"))))
        .isEqualTo(new NullMarkedPackages(0, 0));
  }
}
