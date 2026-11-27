package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The GitHub Actions problem matcher must match what javac and Sterling print. */
class ProblemMatcherTest {
  private static final String SPOTBUGS_LINE =
      "src/main/java/app/Bad.java:6 [SpotBugs:NP_ALWAYS_NULL] Null pointer dereference of text";
  private static final String COVERAGE_LINE =
      "src/main/java/app/Calc.java [JaCoCo:INSTRUCTION] 12 of 40 missed";

  // The matcher's patterns, in file order: javac's, then console format 1's.
  private static List<Pattern> patterns() throws IOException {
    Path file =
        Path.of(System.getProperty("sterling.guide")).resolve("sterling-problem-matcher.json");
    Matcher regexps =
        Pattern.compile("\"regexp\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .matcher(Files.readString(file));
    List<Pattern> patterns = new ArrayList<>();
    while (regexps.find()) {
      // JSON string escapes: the file only needs \\ and \".
      patterns.add(Pattern.compile(regexps.group(1).replace("\\\\", "\\").replace("\\\"", "\"")));
    }
    return patterns;
  }

  @Test
  void javacsErrorAndWarningLinesAreMatchedWithFileLineSeverityAndMessage() throws IOException {
    Matcher error =
        patterns()
            .get(0)
            .matcher(
                "/work/repo/src/main/java/app/App.java:11: error: [NullAway] dereferenced expression"
                    + " 'find()' is @Nullable");
    Matcher warning =
        patterns()
            .get(0)
            .matcher("src/main/java/app/App.java:9: warning: [MissingOverride] toString() should");

    assertThat(error.matches()).isTrue();
    assertThat(error.group(1)).isEqualTo("/work/repo/src/main/java/app/App.java");
    assertThat(error.group(2)).isEqualTo("11");
    assertThat(error.group(3)).isEqualTo("error");
    assertThat(error.group(4)).startsWith("[NullAway]");
    assertThat(warning.matches()).isTrue();
    assertThat(warning.group(3)).isEqualTo("warning");
  }

  @Test
  void aSpotbugsLineIsMatchedWithFileLineCodeAndMessage() throws IOException {
    Matcher matcher = patterns().get(1).matcher(SPOTBUGS_LINE);

    assertThat(matcher.matches()).isTrue();
    assertThat(matcher.group(1)).isEqualTo("src/main/java/app/Bad.java");
    assertThat(matcher.group(2)).isEqualTo("6");
    assertThat(matcher.group(3)).isEqualTo("SpotBugs:NP_ALWAYS_NULL");
    assertThat(matcher.group(4)).isEqualTo("Null pointer dereference of text");
  }

  @Test
  void aCoverageLineIsMatchedWithAFileAndNoLine() throws IOException {
    Matcher matcher = patterns().get(1).matcher(COVERAGE_LINE);

    assertThat(matcher.matches()).isTrue();
    assertThat(matcher.group(1)).isEqualTo("src/main/java/app/Calc.java");
    assertThat(matcher.group(2)).isNull();
    assertThat(matcher.group(3)).isEqualTo("JaCoCo:INSTRUCTION");
    assertThat(matcher.group(4)).isEqualTo("12 of 40 missed");
  }

  @Test
  void linesSterlingDoesntPrintAsFindingsAreNotMatched() throws IOException {
    for (Pattern pattern : patterns()) {
      for (String other :
          List.of(
              "Coverage is below the thresholds:",
              "  instruction: 68.0% (below minimum 80%)",
              "> Task :compileJava",
              "... and 3 more: build/reports/spotbugs/main.html")) {
        assertThat(pattern.matcher(other).matches()).describedAs(other).isFalse();
      }
    }
  }
}
