package dev.balakmran.sterling.internal;

import java.util.List;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * Raises javac's warning cap while {@code sterling.errorProne.errorsAsWarnings} is on, and rejects
 * it beside {@code warningsAsErrors}.
 *
 * <p>javac stops printing after 100 warnings by default, which would hide most of a brownfield
 * codebase's findings once Error Prone's errors are demoted. An argument provider rather than a
 * plain compiler argument, so the settings are task inputs.
 */
public final class ErrorsAsWarnings implements CommandLineArgumentProvider {
  /** The warning cap passed to javac as {@code -Xmaxwarns}, in place of its default of 100. */
  public static final int MAX_WARNINGS = 10_000;

  private final Provider<Boolean> errorsAsWarnings;
  private final Provider<Boolean> warningsAsErrors;
  private final Provider<Boolean> errorProneEnabled;

  /**
   * Creates the provider.
   *
   * @param errorsAsWarnings the {@code errorsAsWarnings} setting
   * @param warningsAsErrors the {@code warningsAsErrors} setting
   * @param errorProneEnabled the {@code errorProne.enabled} setting
   */
  public ErrorsAsWarnings(
      Provider<Boolean> errorsAsWarnings,
      Provider<Boolean> warningsAsErrors,
      Provider<Boolean> errorProneEnabled) {
    this.errorsAsWarnings = errorsAsWarnings;
    this.warningsAsErrors = warningsAsErrors;
    this.errorProneEnabled = errorProneEnabled;
  }

  /**
   * Whether Error Prone's errors are demoted and its warnings are counted.
   *
   * @return true while {@code errorsAsWarnings} and Error Prone are both on
   * @throws InvalidUserDataException if {@code errorsAsWarnings} and {@code warningsAsErrors} are
   *     both true
   */
  @Input
  public boolean isEnabled() {
    if (errorsAsWarnings.get() && warningsAsErrors.get()) {
      throw new InvalidUserDataException(
          "sterling.errorProne.errorsAsWarnings and sterling.errorProne.warningsAsErrors are both"
              + " true: -Werror would turn the demoted errors back into failures. Turn one off;"
              + " the LENIENT profile sets errorsAsWarnings, and STRICT sets warningsAsErrors.");
    }
    return errorsAsWarnings.get() && errorProneEnabled.get();
  }

  @Override
  public Iterable<String> asArguments() {
    return isEnabled() ? List.of("-Xmaxwarns", String.valueOf(MAX_WARNINGS)) : List.of();
  }
}
