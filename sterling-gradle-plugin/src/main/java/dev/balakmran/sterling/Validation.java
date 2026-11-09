package dev.balakmran.sterling;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.provider.Provider;

final class Validation {
  private Validation() {}

  /** Fails when read if the value is outside 0..100, naming the setting. */
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
