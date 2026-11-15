package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.gradle.api.Project;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoverageExclusionTest {
  private static final List<String> CLASSES =
      List.of(
          "app/Calc.class",
          "app/Calc$1.class",
          "app/OrderMapperImpl.class",
          "app/OrderMapperImpl$1.class",
          "app/OrderMapperImplTest.class",
          "gen/api/OrderDto.class",
          "gen/api/OrderDto$Builder.class",
          "gen/api/OrderDtoMapper.class",
          "gen/api/notes.txt");

  @TempDir Path dir;

  private Set<String> remaining(Spec<FileTreeElement> exclusion) throws IOException {
    Path classes = dir.resolve("classes");
    for (String path : CLASSES) {
      Path file = classes.resolve(path);
      Files.createDirectories(file.getParent());
      Files.writeString(file, "");
    }
    Project project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build();
    Set<String> left = new TreeSet<>();
    project
        .fileTree(classes)
        .matching(pattern -> pattern.exclude(exclusion))
        .visit(element -> {
          if (!element.isDirectory()) {
            left.add(element.getRelativePath().getPathString());
          }
        });
    return left;
  }

  @Test
  void leavesOutGeneratedClassesAndTheirNestedClassesOnly() throws IOException {
    File list = dir.resolve("generated-classes.txt").toFile();
    Files.writeString(list.toPath(), "app.OrderMapperImpl\ngen.api.OrderDto\n");

    assertThat(remaining(new CoverageExclusion(list)))
        .containsExactly(
            "app/Calc$1.class",
            "app/Calc.class",
            "app/OrderMapperImplTest.class",
            "gen/api/OrderDtoMapper.class",
            "gen/api/notes.txt");
  }

  @Test
  void withoutAListLeavesEverything() throws IOException {
    assertThat(remaining(new CoverageExclusion(dir.resolve("missing.txt").toFile())))
        .hasSize(CLASSES.size());
  }
}
