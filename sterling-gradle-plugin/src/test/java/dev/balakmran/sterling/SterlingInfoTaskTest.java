package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.balakmran.sterling.internal.SterlingInfoTask;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class SterlingInfoTaskTest {
  private final Project project = ProjectBuilder.builder().build();
  private final SterlingExtension sterling;

  SterlingInfoTaskTest() {
    project.getPluginManager().apply(SterlingPlugin.class);
    sterling = project.getExtensions().getByType(SterlingExtension.class);
  }

  private SterlingInfoTask task() {
    return project.getTasks().named(SterlingPlugin.INFO, SterlingInfoTask.class).get();
  }

  private String render() {
    return task().render("25");
  }

  @Test
  void rendersTheStandardDefaults() {
    String expected =
        String.join(
            "\n",
            "Sterling " + ToolVersions.get("sterling"),
            "Profile:              STANDARD",
            "Gate wired to check:  yes",
            "Daemon JVM:           25",
            "Java toolchain:       not active (java plugin not applied)",
            "Formatting:           on   (google-java-format "
                + ToolVersions.get("google-java-format")
                + " via Spotless "
                + ToolVersions.get("spotless")
                + ")",
            "Error Prone:          on   ("
                + ToolVersions.get("errorprone")
                + "; warnings are not errors)",
            "NullAway:             on   ("
                + ToolVersions.get("nullaway")
                + "; JSpecify "
                + ToolVersions.get("jspecify")
                + "; @NullMarked code only)",
            "SpotBugs:             on   ("
                + ToolVersions.get("spotbugs")
                + "; effort default; medium confidence and up)",
            "Coverage:             on   (JaCoCo "
                + ToolVersions.get("jacoco")
                + "; instruction >= 80%, branch >= 70%)");

    assertThat(render()).isEqualTo(expected);
  }

  @Test
  void rendersCountsOffStatesAndTheConsumerFilter() {
    sterling.getErrorProne().disable("UnusedVariable");
    sterling.getSpotbugs().getExcludeFilter().set(project.file("exclude.xml"));
    sterling.getCoverage().getExcludes().addAll(List.of("**/*Application*", "**/generated/**"));
    sterling.getFormat().getEnabled().set(false);

    assertThat(render())
        .contains("Formatting:           off\n")
        .contains("warnings are not errors; 1 check disabled)")
        .contains("; consumer filter merged)")
        .contains("branch >= 70%; 2 excludes)");
  }

  @Test
  void coverageListsTheCountedTestTasks() {
    project
        .getTasks()
        .named(SterlingPlugin.INFO, SterlingInfoTask.class)
        .configure(task -> task.getCoverageTestTasks().set(List.of("test", "integrationTest")));

    assertThat(render()).contains("branch >= 70%; test, integrationTest)");
  void formattingReportsAStyleOtherThanGoogle() {
    sterling.getFormat().getStyle().set(FormatSettings.Style.AOSP);

    assertThat(render())
        .contains(
            "(google-java-format "
                + ToolVersions.get("google-java-format")
                + ", AOSP style via Spotless ");
  }

  @Test
  void formattingReportsTheStyleAndRatchet() {
    sterling.getFormat().getStyle().set(FormatSettings.Style.AOSP);
    sterling.getFormat().getRatchetFrom().set("origin/main");

    assertThat(render())
        .contains(
            ", AOSP style, ratchet from origin/main via Spotless " + ToolVersions.get("spotless"));
  }

  @Test
  void anEmptyRatchetFromFailsWithTheSettingNamed() {
    sterling.getFormat().getRatchetFrom().set("");

    assertThatThrownBy(this::render)
        .rootCause()
        .isInstanceOf(InvalidUserDataException.class)
        .hasMessageContaining("sterling.format.ratchetFrom must not be empty");
  }

  @Test
  void nullAwayReportsJspecifyModeWhenOn() {
    sterling.getProfile().set(Profile.STRICT);

    assertThat(render()).contains("; @NullMarked code only; JSpecify mode)");
  }

  @Test
  void spotbugsReportsTheProfilesEffortAndReportLevel() {
    sterling.getProfile().set(Profile.STRICT);

    assertThat(render()).contains("; effort max; medium confidence and up)");
  }

  @Test
  void nullAwayReportsErrorProneOff() {
    sterling.getErrorProne().getEnabled().set(false);

    assertThat(render()).contains("NullAway:             off (Error Prone off)");
  }

  @Test
  void jsonHasExactlyTheSchemaVersion1Keys() throws IOException {
    SterlingInfoTask task = task();
    task.getJavaToolchain().set("25");
    task.getSpotbugsReports().put("xml", "build/reports/spotbugs/main.xml");
    task.getSpotbugsReports().put("sarif", "build/reports/spotbugs/main.sarif");
    task.getCoverageReports().put("xml", "build/reports/jacoco/test/jacocoTestReport.xml");

    List<String> keys = new ArrayList<>();
    collectKeys("", task.jsonModel("25"), keys);

    // Removing or renaming a key, or changing a value's type, must raise the schema version.
    assertThat(SterlingInfoTask.SCHEMA_VERSION).isEqualTo(1);
    assertThat(keys).containsExactlyElementsOf(schemaKeys());
  }

  @Test
  void jsonIsOneLineWithTheResolvedValues() {
    sterling.getErrorProne().disable("UnusedVariable", "MissingSummary");
    sterling.getCoverage().getInstruction().set(90);

    String json = task().renderJson("25");

    assertThat(json)
        .doesNotContain("\n")
        .startsWith("{\"schemaVersion\":1,\"sterling\":\"" + ToolVersions.get("sterling") + "\"")
        .contains("\"project\":\":\",\"profile\":\"STANDARD\",\"profileProperty\":null")
        .contains("\"daemonJvm\":25")
        .contains("\"disabledChecks\":[\"MissingSummary\",\"UnusedVariable\"]")
        .contains("\"instruction\":90,\"branch\":70,\"excludes\":[]")
        .contains("\"excludeFilter\":null");
  }

  @Test
  void jsonReportsJavaNotActiveWithoutReports() {
    Map<String, Object> model = task().jsonModel("25");

    assertThat(model.get("javaToolchain"))
        .isEqualTo(Map.of("active", false, "reason", "java plugin not applied"));
    assertThat(((Map<?, ?>) model.get("spotbugs")).containsKey("reports")).isFalse();
    assertThat(((Map<?, ?>) model.get("coverage")).containsKey("reports")).isFalse();
  }

  @Test
  void jsonReportsNullAwayInactiveWhileErrorProneIsOff() {
    sterling.getErrorProne().getEnabled().set(false);

    Map<?, ?> nullAway = (Map<?, ?>) task().jsonModel("25").get("nullAway");

    assertThat(nullAway.get("enabled")).isEqualTo(true);
    assertThat(nullAway.get("active")).isEqualTo(false);
  }

  @Test
  void jsonReportsTheFormatStyleAndRatchet() {
    Map<?, ?> defaults = (Map<?, ?>) task().jsonModel("25").get("format");
    assertThat(defaults.get("style")).isEqualTo("GOOGLE");
    assertThat(defaults.containsKey("ratchetFrom")).isTrue();
    assertThat(defaults.get("ratchetFrom")).isNull();
  }

  @Test
  void jsonReportsASetStyleAndRatchet() {
    sterling.getFormat().getStyle().set(FormatSettings.Style.AOSP);
    sterling.getFormat().getRatchetFrom().set("origin/main");

    Map<?, ?> format = (Map<?, ?>) task().jsonModel("25").get("format");

    assertThat(format.get("style")).isEqualTo("AOSP");
    assertThat(format.get("ratchetFrom")).isEqualTo("origin/main");
  }

  @Test
  void jsonGivesEachVersionItsSource() {
    SterlingInfoTask task = task();
    task.getVersionOverrides()
        .set(Map.of("errorprone", "9.9.9", "jspecify", SterlingInfoTask.MANAGED));

    Map<String, Object> model = task.jsonModel("25");
    Map<?, ?> errorProne = (Map<?, ?>) model.get("errorProne");
    Map<?, ?> nullAway = (Map<?, ?>) model.get("nullAway");
    Map<?, ?> coverage = (Map<?, ?>) model.get("coverage");

    assertThat(errorProne.get("version")).isEqualTo("9.9.9");
    assertThat(errorProne.get("versionSource")).isEqualTo("project");
    assertThat(nullAway.get("version")).isEqualTo(ToolVersions.get("nullaway"));
    assertThat(nullAway.get("versionSource")).isEqualTo("sterling");
    assertThat(nullAway.containsKey("jspecify")).isTrue();
    assertThat(nullAway.get("jspecify")).isNull();
    assertThat(nullAway.get("jspecifySource")).isEqualTo("project");
    assertThat(coverage.get("versionSource")).isEqualTo("sterling");
  }

  // Dotted key paths in output order: "format.versions.spotless".
  private static void collectKeys(String prefix, Map<?, ?> map, List<String> keys) {
    map.forEach((key, value) -> {
      String path = prefix + key;
      keys.add(path);
      if (value instanceof Map<?, ?> nested) {
        collectKeys(path + ".", nested, keys);
      }
    });
  }

  private static List<String> schemaKeys() throws IOException {
    try (InputStream in = SterlingInfoTaskTest.class.getResourceAsStream("stl-info-v1-keys.txt")) {
      assertThat(in).describedAs("stl-info-v1-keys.txt").isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .filter(line -> !line.isBlank() && !line.startsWith("#"))
          .toList();
    }
  }
}
