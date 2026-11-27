package dev.balakmran.sterling.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

class CoverageSummaryTaskTest {
  private static final Function<String, String> UNCHANGED = Function.identity();

  @TempDir Path dir;

  // JaCoCo's reports start with a document type declaration naming a DTD that isn't there.
  private Element report(String packages, String counters) throws IOException {
    File xml = dir.resolve("jacocoTestReport.xml").toFile();
    Files.writeString(
        xml.toPath(),
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
        <report name="consumer">%s%s</report>
        """
            .formatted(packages, counters));
    return Xml.parseReport(xml, "JaCoCo").getDocumentElement();
  }

  private static String sourceFile(String name, int missed, int covered) {
    return "<sourcefile name=\"%s\"><counter type=\"INSTRUCTION\" missed=\"%d\" covered=\"%d\"/>"
            .formatted(name, missed, covered)
        + "</sourcefile>";
  }

  @Test
  void printsBothMeasurementsThenTheMostMissedFiles() throws IOException {
    Element report =
        report(
            "<package name=\"com/example\">"
                + sourceFile("Small.java", 2, 8)
                + sourceFile("Big.java", 30, 10)
                + sourceFile("Covered.java", 0, 50)
                + "</package>",
            "<counter type=\"INSTRUCTION\" missed=\"32\" covered=\"68\"/>"
                + "<counter type=\"BRANCH\" missed=\"1\" covered=\"3\"/>");

    assertThat(CoverageSummaryTask.render(report, 80, 70, UNCHANGED))
        .containsExactly(
            "Coverage is below the thresholds:",
            "  instruction: 68.0% (below minimum 80%)",
            "  branch: 75.0% (meets minimum 70%)",
            "com/example/Big.java [JaCoCo:INSTRUCTION] 30 of 40 missed",
            "com/example/Small.java [JaCoCo:INSTRUCTION] 2 of 10 missed");
  }

  @Test
  void namesEachFileByItsSourcePathAndFallsBackToThePackagePath() throws IOException {
    Element report =
        report(
            "<package name=\"com/example\">"
                + sourceFile("Found.java", 30, 10)
                + sourceFile("Lost.java", 20, 10)
                + "</package>",
            "<counter type=\"INSTRUCTION\" missed=\"50\" covered=\"20\"/>");
    Function<String, String> path =
        sourcePath ->
            sourcePath.endsWith("Found.java") ? "src/main/java/" + sourcePath : sourcePath;

    assertThat(CoverageSummaryTask.render(report, 80, 70, path))
        .contains(
            "src/main/java/com/example/Found.java [JaCoCo:INSTRUCTION] 30 of 40 missed",
            "com/example/Lost.java [JaCoCo:INSTRUCTION] 20 of 30 missed");
  }

  @Test
  void printsNothingWhenBothMeasurementsMeetTheirThresholds() throws IOException {
    Element report =
        report(
            "<package name=\"app\">" + sourceFile("A.java", 1, 9) + "</package>",
            "<counter type=\"INSTRUCTION\" missed=\"1\" covered=\"9\"/>");

    assertThat(CoverageSummaryTask.render(report, 80, 70, UNCHANGED)).isEmpty();
  }

  @Test
  void roundsDownSoAShortfallNeverPrintsAsTheThreshold() throws IOException {
    Element report = report("", "<counter type=\"INSTRUCTION\" missed=\"2001\" covered=\"7999\"/>");

    assertThat(CoverageSummaryTask.render(report, 80, 70, UNCHANGED))
        .contains(
            "  instruction: 79.9% (below minimum 80%)", "  branch: nothing to cover (minimum 70%)");
  }

  @Test
  void printsAtMostTenFilesAndNamesDefaultPackageFilesAlone() throws IOException {
    StringBuilder files = new StringBuilder("<package name=\"\">");
    for (int i = 1; i <= 12; i++) {
      files.append(sourceFile("F" + i + ".java", i, 1));
    }
    files.append("</package>");

    List<String> lines =
        CoverageSummaryTask.render(
            report(
                files.toString(), "<counter type=\"INSTRUCTION\" missed=\"78\" covered=\"12\"/>"),
            80,
            70,
            UNCHANGED);

    assertThat(lines).hasSize(3 + CoverageSummaryTask.TOP);
    assertThat(lines.get(3)).isEqualTo("F12.java [JaCoCo:INSTRUCTION] 12 of 13 missed");
  }

  @Test
  void theMarkerStaysOnlyWhenTheVerificationDoesntFinish() throws IOException {
    File marker = dir.resolve("sterling/coverage-verification-failed").toFile();
    Project project = ProjectBuilder.builder().build();
    Task task = project.getTasks().register("verification").get();
    var location = project.getLayout().file(project.provider(() -> marker));

    CoverageSummaryTask.markFailed(location).execute(task);
    assertThat(marker).exists();

    CoverageSummaryTask.clearFailed(location).execute(task);
    assertThat(marker).doesNotExist();
  }
}
