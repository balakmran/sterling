package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The Plugin Portal description is the tagline everywhere Sterling describes itself. */
class TaglineTest {
  private static final String DESCRIPTION = System.getProperty("sterling.description");

  @Test
  void readmeOpensWithTheDescription() {
    assertThat(lineAfterTitle("sterling.readme")).isEqualTo(DESCRIPTION);
  }

  @Test
  void claudeMdOpensWithTheDescription() {
    assertThat(lineAfterTitle("sterling.claude")).isEqualTo(DESCRIPTION);
  }

  @Test
  void packageJavadocStartsWithTheDescription() {
    String javadoc = read("sterling.packageInfo");
    // Joins the comment's lines: drops each leading " * " and collapses the line breaks.
    String text = javadoc.replaceAll("(?m)^\\s*/?\\*+/?\\s?", "").replaceAll("\\s+", " ").trim();

    assertThat(text).startsWith(DESCRIPTION);
  }

  // The first line after the "# Sterling" title that's neither blank nor a badge.
  private static String lineAfterTitle(String property) {
    return read(property)
        .lines()
        .skip(1)
        .filter(line -> !line.isBlank() && !line.startsWith("[!["))
        .findFirst()
        .orElse("");
  }

  private static String read(String property) {
    try {
      return Files.readString(Path.of(System.getProperty(property)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
