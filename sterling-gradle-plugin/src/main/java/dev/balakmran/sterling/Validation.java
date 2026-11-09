package dev.balakmran.sterling;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.provider.Provider;

/** Checks on setting values that can only run once the build script has set them. */
final class Validation {
  private Validation() {}

  /**
   * Wraps a percentage so reading it fails when it's outside 0 to 100.
   *
   * <p>There's no {@code afterEvaluate}, so validation happens when the value is read: when {@code
   * stlInfo} runs or the coverage verification is configured.
   *
   * @param value the setting's value
   * @param setting the setting's path under {@code sterling}, such as {@code coverage.instruction},
   *     used in the error message
   * @return the same value, checked when read
   */
  static Provider<Integer> percent(Provider<Integer> value, String setting) {
    return value.map(v -> {
      if (v < 0 || v > 100) {
        throw new InvalidUserDataException(
            "sterling." + setting + " must be between 0 and 100 (was " + v + ").");
      }
      return v;
    });
  }
}
