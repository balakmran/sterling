package dev.balakmran.sterling;

import java.util.List;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskProvider;

/** Lazy ways to honour an {@code enabled} flag, read only once the build script has run. */
final class Flags {
  private Flags() {}

  /**
   * Tasks to depend on while the flag is on. A {@code flag.map(...)} would hide them from Gradle.
   */
  static Provider<List<TaskProvider<?>>> whenOn(
      ProviderFactory providers, Provider<Boolean> flag, TaskProvider<?>... tasks) {
    List<TaskProvider<?>> targets = List.of(tasks);
    return providers.provider(() -> flag.get() ? targets : List.of());
  }

  /** An {@code onlyIf} condition; a class rather than a lambda so the cache can store it. */
  static Spec<Task> onlyIf(Provider<Boolean> flag) {
    return new FlagSpec(flag);
  }

  private static final class FlagSpec implements Spec<Task> {
    private final Provider<Boolean> flag;

    FlagSpec(Provider<Boolean> flag) {
      this.flag = flag;
    }

    @Override
    public boolean isSatisfiedBy(Task task) {
      return flag.get();
    }
  }
}
