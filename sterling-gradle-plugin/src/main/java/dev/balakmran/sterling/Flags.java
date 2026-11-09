package dev.balakmran.sterling;

import java.util.List;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskProvider;

/**
 * Ways to honour an {@code enabled} flag lazily.
 *
 * <p>The {@code sterling { }} block runs after Sterling is applied, so a flag can't be read when
 * Sterling configures a tool. These helpers defer reading it until Gradle builds the task graph.
 */
final class Flags {
  private Flags() {}

  /**
   * Tasks to depend on, or be finalized by, while the flag is on; none while it's off.
   *
   * <p>Pass the result to {@code dependsOn} or {@code finalizedBy}. It's built with {@code
   * providers.provider(...)} rather than {@code flag.map(...)}: Gradle takes a mapped provider's
   * dependencies from the flag's producers, so it could drop these tasks.
   *
   * @param providers the project's provider factory
   * @param flag the {@code enabled} flag
   * @param tasks the tasks to add while the flag is on
   * @return the tasks, or an empty list
   */
  static Provider<List<TaskProvider<?>>> whenOn(
      ProviderFactory providers, Provider<Boolean> flag, TaskProvider<?>... tasks) {
    List<TaskProvider<?>> targets = List.of(tasks);
    return providers.provider(() -> flag.get() ? targets : List.of());
  }

  /**
   * An {@code onlyIf} condition that holds while the flag is on.
   *
   * <p>It's a class rather than a lambda so the configuration cache can store it.
   *
   * @param flag the {@code enabled} flag
   * @return the condition
   */
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
