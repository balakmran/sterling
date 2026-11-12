package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import net.ltgt.gradle.errorprone.CheckSeverity;
import org.junit.jupiter.api.Test;

class ErrorProneTest {
  @Test
  void generatedPathsMatchBuildGeneratedWithEitherSeparator() {
    assertThat("/home/ci/app/build/generated/sources/openapi/Api.java")
        .matches(ErrorProne.GENERATED_PATHS);
    assertThat("C:\\work\\app\\build\\generated\\aotSources\\App__BeanDefinitions.java")
        .matches(ErrorProne.GENERATED_PATHS);
    assertThat("/home/ci/app/module/build/generated/ap/Mapper_Impl.java")
        .matches(ErrorProne.GENERATED_PATHS);
  }

  @Test
  void generatedPathsLeaveOrdinarySourcesAlone() {
    assertThat("/home/ci/app/src/main/java/app/App.java").doesNotMatch(ErrorProne.GENERATED_PATHS);
    assertThat("/home/ci/app/src/main/java/app/generated/Config.java")
        .doesNotMatch(ErrorProne.GENERATED_PATHS);
    assertThat("/home/ci/rebuild/generated/App.java").doesNotMatch(ErrorProne.GENERATED_PATHS);
  }

  @Test
  void offTurnsEachNamedCheckOff() {
    assertThat(ErrorProne.off(Set.of("UnusedVariable", "NullAway"), true))
        .containsExactlyInAnyOrderEntriesOf(
            java.util.Map.of("UnusedVariable", CheckSeverity.OFF, "NullAway", CheckSeverity.OFF));
  }

  @Test
  void nullAwayOptionsAddJspecifyModeOnlyWhenItIsOn() {
    assertThat(NullAway.options(true, false))
        .containsExactlyEntriesOf(java.util.Map.of("NullAway:OnlyNullMarked", "true"));
    assertThat(NullAway.options(true, true))
        .containsExactlyInAnyOrderEntriesOf(
            java.util.Map.of("NullAway:OnlyNullMarked", "true", "NullAway:JSpecifyMode", "true"));
    assertThat(NullAway.options(false, true)).isEmpty();
  }

  @Test
  void offLeavesNullAwayOutWhileItIsntRunning() {
    assertThat(ErrorProne.off(Set.of("UnusedVariable", "NullAway"), false))
        .containsExactlyEntriesOf(java.util.Map.of("UnusedVariable", CheckSeverity.OFF));
  }
}
