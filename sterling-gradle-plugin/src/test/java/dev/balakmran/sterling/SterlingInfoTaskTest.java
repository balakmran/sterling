package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;

import dev.balakmran.sterling.internal.SterlingInfoTask;
import java.util.List;
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

  private String render() {
    return project.getTasks().named(SterlingPlugin.INFO, SterlingInfoTask.class).get().render("25");
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
                + ")",
            "Error Prone:          on   ("
                + ToolVersions.get("errorprone")
                + "; warnings are not errors)",
            "NullAway:             on   ("
                + ToolVersions.get("nullaway")
                + "; JSpecify "
                + ToolVersions.get("jspecify")
                + "; @NullMarked code only)",
            "SpotBugs:             on   (" + ToolVersions.get("spotbugs") + ")",
            "Coverage:             on   (instruction >= 80%, branch >= 70%)");

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
  void nullAwayReportsJspecifyModeWhenOn() {
    sterling.getProfile().set(Profile.STRICT);

    assertThat(render()).contains("; @NullMarked code only; JSpecify mode)");
  }

  @Test
  void nullAwayReportsErrorProneOff() {
    sterling.getErrorProne().getEnabled().set(false);

    assertThat(render()).contains("NullAway:             off (Error Prone off)");
  }
}
