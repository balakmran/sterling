package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The version resource must match the catalog and version.txt it was generated from. */
class ToolVersionsTest {
  @Test
  void toolVersionsMatchTheVersionCatalog() throws IOException {
    Map<String, String> catalog = catalogVersions();
    Map<String, String> tools = new HashMap<>(ToolVersions.all());
    tools.remove("sterling");

    assertThat(tools.keySet())
        .containsExactlyInAnyOrder(
            "google-java-format",
            "spotless",
            "errorprone",
            "nullaway",
            "jspecify",
            "spotbugs",
            "jacoco");
    tools.forEach(
        (key, version) -> assertThat(catalog.get(key)).describedAs(key).isEqualTo(version));
  }

  @Test
  void sterlingVersionMatchesVersionTxt() throws IOException {
    String expected = Files.readString(Path.of(System.getProperty("sterling.versionFile"))).trim();
    assertThat(ToolVersions.get("sterling")).isEqualTo(expected);
  }

  // Reads `key = "value"` lines from the [versions] table.
  private static Map<String, String> catalogVersions() throws IOException {
    Pattern entry = Pattern.compile("^([\\w-]+)\\s*=\\s*\"([^\"]+)\".*");
    List<String> lines = Files.readAllLines(Path.of(System.getProperty("sterling.catalog")));
    Map<String, String> versions = new HashMap<>();
    boolean inVersions = false;
    for (String raw : lines) {
      String line = raw.trim();
      if (line.startsWith("[")) {
        inVersions = line.equals("[versions]");
        continue;
      }
      Matcher match = entry.matcher(line);
      if (inVersions && match.matches()) {
        versions.put(match.group(1), match.group(2));
      }
    }
    return versions;
  }
}
