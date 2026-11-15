package dev.balakmran.sterling.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratedClassesTaskTest {
  @TempDir Path dir;

  @Test
  void namesGeneratedClassesFromTheirSourcePaths() throws IOException {
    Path openapi = dir.resolve("openapi");
    write(openapi.resolve("gen/api/OrdersApi.java"), "");
    write(openapi.resolve("gen/api/package-info.java"), "");
    write(openapi.resolve("gen/Model.java"), "");
    write(openapi.resolve("README.md"), "");
    Path processor = dir.resolve("annotationProcessor");
    write(processor.resolve("app/OrderMapperImpl.java"), "");

    List<String> names =
        GeneratedClassesTask.generatedClasses(
            Set.of(openapi.toFile(), processor.toFile(), dir.resolve("missing").toFile()));

    assertThat(names).containsExactly("app.OrderMapperImpl", "gen.Model", "gen.api.OrdersApi");
  }

  @Test
  void readsOneNamePerLineSkippingBlankLines() throws IOException {
    Path list = dir.resolve("generated-classes.txt");
    write(list, "app.OrderMapperImpl\n\ngen.Model\n");

    assertThat(GeneratedClassesTask.read(list.toFile()))
        .containsExactly("app.OrderMapperImpl", "gen.Model");
  }

  @Test
  void readsAMissingListAsEmpty() {
    assertThat(GeneratedClassesTask.read(dir.resolve("missing.txt").toFile())).isEmpty();
  }

  private static void write(Path file, String text) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
