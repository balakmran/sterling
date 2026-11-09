package dev.balakmran.sterling.internal;

import java.util.List;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * Adds {@code -Werror} to javac while {@code sterling.errorProne.warningsAsErrors} is on.
 *
 * <p>An argument provider rather than a plain compiler argument, so the flag is a task input:
 * changing it reruns compilation instead of leaving the task up to date.
 */
public final class WarningsAsErrors implements CommandLineArgumentProvider {
  private final Provider<Boolean> enabled;

  /**
   * Creates the provider.
   *
   * @param enabled the {@code warningsAsErrors} setting
   */
  public WarningsAsErrors(Provider<Boolean> enabled) {
    this.enabled = enabled;
  }

  /**
   * Whether warnings are errors.
   *
   * @return the setting's current value
   */
  @Input
  public boolean isEnabled() {
    return enabled.get();
  }

  @Override
  public Iterable<String> asArguments() {
    return isEnabled() ? List.of("-Werror") : List.of();
  }
}
