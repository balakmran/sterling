package dev.balakmran.sterling.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.provider.Provider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

/** The javac warning cap and the conflict check for errorsAsWarnings. */
class ErrorsAsWarningsTest {
  private static Provider<Boolean> of(boolean value) {
    return ProjectBuilder.builder().build().getProviders().provider(() -> value);
  }

  private static ErrorsAsWarnings provider(boolean errors, boolean warnings, boolean enabled) {
    return new ErrorsAsWarnings(of(errors), of(warnings), of(enabled));
  }

  @Test
  void raisesTheWarningCapWhileOnWithErrorProne() {
    assertThat(provider(true, false, true).asArguments())
        .containsExactly("-Xmaxwarns", String.valueOf(ErrorsAsWarnings.MAX_WARNINGS));
  }

  @Test
  void addsNothingWhenOffOrWhenErrorProneIsOff() {
    assertThat(provider(false, false, true).asArguments()).isEqualTo(List.of());
    assertThat(provider(true, false, false).asArguments()).isEqualTo(List.of());
  }

  @Test
  void rejectsErrorsAsWarningsWithWarningsAsErrors() {
    assertThatThrownBy(() -> provider(true, true, true).asArguments())
        .isInstanceOf(InvalidUserDataException.class)
        .hasMessageContaining("errorsAsWarnings")
        .hasMessageContaining("warningsAsErrors");
  }
}
