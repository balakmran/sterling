package dev.balakmran.sterling.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * Error Prone's patch flags for an {@code stlFix} compilation, skipping checks the build turned
 * off.
 *
 * <p>An Error Prone argument provider, so the flags reach Error Prone itself rather than javac, and
 * the checks are a task input: a patching run never shares a cache key with a normal one. Each
 * patched check is also enabled at {@code WARN}, since Error Prone only runs enabled checks, and
 * those flags come last so they override the build's own settings for the run.
 */
public final class PatchChecks implements CommandLineArgumentProvider {
  private final Provider<Set<String>> checks;
  private final Provider<Set<String>> off;

  /**
   * Creates the provider.
   *
   * @param checks the {@code patchChecks} setting
   * @param off the checks the build turned off, which {@code disable} records
   */
  public PatchChecks(Provider<Set<String>> checks, Provider<Set<String>> off) {
    this.checks = checks;
    this.off = off;
  }

  /**
   * The checks to patch: the setting, less any the build turned off.
   *
   * @return the check names, sorted
   */
  @Input
  public Set<String> getChecks() {
    Set<String> on = new TreeSet<>(checks.get());
    on.removeAll(off.get());
    return on;
  }

  @Override
  public Iterable<String> asArguments() {
    Set<String> on = getChecks();
    if (on.isEmpty()) {
      return List.of();
    }
    List<String> arguments = new ArrayList<>();
    on.forEach(check -> arguments.add("-Xep:" + check + ":WARN"));
    arguments.add("-XepPatchChecks:" + String.join(",", on));
    arguments.add("-XepPatchLocation:IN_PLACE");
    return arguments;
  }
}
