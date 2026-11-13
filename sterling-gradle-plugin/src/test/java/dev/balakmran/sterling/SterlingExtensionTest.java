package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.balakmran.sterling.SpotbugsSettings.Confidence;
import dev.balakmran.sterling.SpotbugsSettings.Effort;
import java.util.List;
import java.util.Map;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class SterlingExtensionTest {
  private final Project project = ProjectBuilder.builder().build();
  private final SterlingExtension sterling;

  SterlingExtensionTest() {
    project.getPluginManager().apply(SterlingPlugin.class);
    sterling = project.getExtensions().getByType(SterlingExtension.class);
  }

  @Test
  void defaultsToStandard() {
    assertThat(sterling.getProfile().get()).isEqualTo(Profile.STANDARD);
  }

  @Test
  void profileSetsEveryDefault() {
    // attachToCheck, warningsAsErrors, jspecifyMode, spotbugs, effort, reportLevel, coverage,
    // instruction, branch
    Map<Profile, List<Object>> expected =
        Map.of(
            Profile.LENIENT,
            List.of(false, false, false, false, Effort.DEFAULT, Confidence.HIGH, false, 0, 0),
            Profile.STANDARD,
            List.of(true, false, false, true, Effort.DEFAULT, Confidence.MEDIUM, true, 80, 70),
            Profile.STRICT,
            List.of(true, true, true, true, Effort.MAX, Confidence.MEDIUM, true, 95, 85));

    expected.forEach((profile, values) -> {
      sterling.getProfile().set(profile);

      List<Object> actual =
          List.of(
              sterling.getAttachToCheck().get(),
              sterling.getErrorProne().getWarningsAsErrors().get(),
              sterling.getNullAway().getJspecifyMode().get(),
              sterling.getSpotbugs().getEnabled().get(),
              sterling.getSpotbugs().getEffort().get(),
              sterling.getSpotbugs().getReportLevel().get(),
              sterling.getCoverage().getEnabled().get(),
              sterling.getCoverage().getInstruction().get(),
              sterling.getCoverage().getBranch().get());
      assertThat(actual).describedAs(profile.name()).isEqualTo(values);
      assertThat(sterling.getFormat().getEnabled().get()).isTrue();
      assertThat(sterling.getErrorProne().getEnabled().get()).isTrue();
      assertThat(sterling.getNullAway().getEnabled().get()).isTrue();
    });
  }

  @Test
  void explicitValuesWinOverTheProfile() {
    sterling.getProfile().set(Profile.LENIENT);
    sterling.getAttachToCheck().set(true);
    sterling.getSpotbugs().getEnabled().set(true);
    sterling.getCoverage().getInstruction().set(90);
    sterling.getNullAway().getJspecifyMode().set(true);
    sterling.getSpotbugs().getEffort().set(Effort.MIN);
    sterling.getSpotbugs().getReportLevel().set(Confidence.LOW);

    assertThat(sterling.getAttachToCheck().get()).isTrue();
    assertThat(sterling.getNullAway().getJspecifyMode().get()).isTrue();
    assertThat(sterling.getSpotbugs().getEnabled().get()).isTrue();
    assertThat(sterling.getSpotbugs().getEffort().get()).isEqualTo(Effort.MIN);
    assertThat(sterling.getSpotbugs().getReportLevel().get()).isEqualTo(Confidence.LOW);
    assertThat(sterling.getCoverage().getInstruction().get()).isEqualTo(90);
    assertThat(sterling.getCoverage().getBranch().get()).isEqualTo(0);
  }

  @Test
  void disableAddsToTheDisabledChecks() {
    sterling.errorProne(errorProne -> errorProne.disable("UnusedVariable", "DeadException"));
    sterling.errorProne(errorProne -> errorProne.disable("MissingOverride"));

    assertThat(sterling.getErrorProne().getDisabledChecks().get())
        .containsExactlyInAnyOrder("UnusedVariable", "DeadException", "MissingOverride");
  }

  @Test
  void percentAccepts0To100AndNamesTheSettingOtherwise() {
    var instruction = sterling.getCoverage().getInstruction();

    instruction.set(100);
    assertThat(Validation.percent(instruction, "coverage.instruction").get()).isEqualTo(100);

    instruction.set(-1);
    assertThatThrownBy(() -> Validation.percent(instruction, "coverage.instruction").get())
        .isInstanceOf(InvalidUserDataException.class)
        .hasMessage("sterling.coverage.instruction must be between 0 and 100 (was -1).");
  }
}
