package dev.balakmran.sterling.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

class SpotbugsSummaryTaskTest {
  private static final Function<String, String> UNDER_SRC = path -> "src/main/java/" + path;

  @TempDir Path dir;

  private Element report(String bugs) throws IOException {
    File xml = dir.resolve("main.xml").toFile();
    Files.writeString(
        xml.toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><BugCollection version=\"4.10.5\">"
            + bugs
            + "</BugCollection>");
    return Xml.parseReport(xml, "SpotBugs").getDocumentElement();
  }

  private static String bug(String type, int line) {
    return """
    <BugInstance type="%s" priority="1">
      <ShortMessage>Null pointer dereference</ShortMessage>
      <LongMessage>Null pointer dereference of text in com.example.Foo.length()</LongMessage>
      <Class classname="com.example.Foo" primary="true">
        <SourceLine classname="com.example.Foo" start="3" end="9" sourcepath="com/example/Foo.java"/>
      </Class>
      <SourceLine classname="com.example.Foo" start="%d" end="%d" sourcepath="com/example/Foo.java"/>
      <SourceLine classname="com.example.Foo" primary="true" start="%d" end="%d" sourcepath="com/example/Foo.java"/>
    </BugInstance>
    """
        .formatted(type, line - 1, line - 1, line, line);
  }

  @Test
  void printsPathLinePatternAndMessage() throws IOException {
    assertThat(
            SpotbugsSummaryTask.render(
                report(bug("NP_ALWAYS_NULL", 42)), UNDER_SRC, "build/reports/spotbugs/main.html"))
        .containsExactly(
            "src/main/java/com/example/Foo.java:42 [SpotBugs:NP_ALWAYS_NULL] Null pointer"
                + " dereference of text in com.example.Foo.length()");
  }

  @Test
  void findingsCarryTheKindMessageAbsoluteSourceFileAndLine() throws IOException {
    Path source = dir.resolve("src/main/java/com/example/Foo.java");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "class Foo {}\n");

    List<Finding> findings =
        SpotbugsSummaryTask.findings(
            report(
                bug("NP_ALWAYS_NULL", 42).replace("priority=\"1\"", "priority=\"1\" rank=\"5\"")),
            UNDER_SRC,
            dir.toFile());

    assertThat(findings)
        .containsExactly(
            new Finding(
                "np-always-null",
                "NP_ALWAYS_NULL",
                "Null pointer dereference of text in com.example.Foo.length()",
                source.toFile().getAbsolutePath(),
                42,
                "SpotBugs rank 5, priority 1",
                true));
  }

  @Test
  void aFindingWhoseSourceIsntFoundHasNoLocationAndAMildRankIsAWarning() throws IOException {
    String bug = bug("SE_BAD_FIELD", 7).replace("priority=\"1\"", "priority=\"2\" rank=\"12\"");

    Finding finding = SpotbugsSummaryTask.findings(report(bug), UNDER_SRC, dir.toFile()).get(0);

    assertThat(finding.file()).isNull();
    assertThat(finding.line()).isZero();
    assertThat(finding.error()).isFalse();
  }

  @Test
  void fallsBackToTheClassLocationAndTheShortMessage() throws IOException {
    String bug =
        """
        <BugInstance type="SE_BAD_FIELD">
          <ShortMessage>Non-serializable field</ShortMessage>
          <Class classname="com.example.Foo">
            <SourceLine classname="com.example.Foo" start="3" end="9" sourcepath="com/example/Foo.java"/>
          </Class>
        </BugInstance>
        """;

    assertThat(SpotbugsSummaryTask.render(report(bug), UNDER_SRC, "main.html"))
        .containsExactly(
            "src/main/java/com/example/Foo.java:3 [SpotBugs:SE_BAD_FIELD] Non-serializable field");
  }

  @Test
  void printsFiftyThenTheRestAsOneLine() throws IOException {
    StringBuilder bugs = new StringBuilder();
    for (int i = 0; i < 53; i++) {
      bugs.append(bug("NP_ALWAYS_NULL", i + 10));
    }

    List<String> lines =
        SpotbugsSummaryTask.render(
            report(bugs.toString()), UNDER_SRC, "build/reports/spotbugs/main.html");

    assertThat(lines).hasSize(SpotbugsSummaryTask.LIMIT + 1);
    assertThat(lines.get(SpotbugsSummaryTask.LIMIT))
        .isEqualTo("... and 3 more: build/reports/spotbugs/main.html");
  }

  @Test
  void noFindingsPrintNothing() throws IOException {
    assertThat(SpotbugsSummaryTask.render(report(""), UNDER_SRC, "main.html")).isEmpty();
  }
}
