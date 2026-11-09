package dev.balakmran.sterling;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.provider.Provider;

/** Adds tool dependencies to a project's configurations only while the tool is on. */
final class ToolDependencies {
  private ToolDependencies() {}

  /**
   * Adds a dependency to a configuration when it's resolved, if the flag is on and the project
   * hasn't declared that module there itself.
   *
   * <p>The check runs in {@code withDependencies}, so the flag is read after the build script, and
   * a tool that's off is never downloaded. A project that declares the module, at any version,
   * keeps its own declaration; that's also how it can move to a newer version than Sterling pins.
   *
   * @param project the project
   * @param configuration the configuration to add to, such as {@code errorprone}
   * @param group the module's group
   * @param name the module's name
   * @param version the version to add
   * @param flag whether the tool is on
   */
  static void addWhenOn(
      Project project,
      String configuration,
      String group,
      String name,
      String version,
      Provider<Boolean> flag) {
    // Created now: the action below runs at resolution time, when Project mustn't be touched.
    Dependency dependency = project.getDependencies().create(group + ":" + name + ":" + version);
    project
        .getConfigurations()
        .named(configuration)
        .configure(target ->
            target.withDependencies(dependencies -> {
              boolean declared =
                  dependencies.stream()
                      .anyMatch(d -> group.equals(d.getGroup()) && name.equals(d.getName()));
              if (flag.get() && !declared) {
                dependencies.add(dependency.copy());
              }
            }));
  }
}
