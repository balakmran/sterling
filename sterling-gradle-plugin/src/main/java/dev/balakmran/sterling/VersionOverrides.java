package dev.balakmran.sterling;

import com.github.spotbugs.snom.SpotBugsExtension;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.provider.Provider;
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension;

/**
 * Finds the tools whose version a project chose instead of Sterling's pin.
 *
 * <p>Error Prone and NullAway are overridden by declaring them in the {@code errorprone}
 * configuration, and JSpecify by declaring it in {@code compileOnly}: {@link ToolDependencies} then
 * leaves its own out. SpotBugs and JaCoCo are overridden through their extensions' {@code
 * toolVersion}. Only declared dependencies are read, so finding overrides resolves nothing.
 */
final class VersionOverrides {
  /** Shown for a dependency declared without a version, such as one a platform manages. */
  static final String MANAGED = "managed";

  private VersionOverrides() {}

  /**
   * The project's overrides, as a provider read once every build script has run.
   *
   * @param project a project with Sterling's tools configured
   * @return the overridden tools' versions, keyed by the names {@link ToolVersions} uses
   */
  static Provider<Map<String, String>> of(Project project) {
    ConfigurationContainer configurations = project.getConfigurations();
    Provider<String> spotbugs =
        project.getExtensions().getByType(SpotBugsExtension.class).getToolVersion();
    JacocoPluginExtension jacocoExtension =
        project.getExtensions().getByType(JacocoPluginExtension.class);
    Provider<String> jacoco = project.getProviders().provider(jacocoExtension::getToolVersion);
    return configurations
        .named("errorprone")
        .zip(configurations.named("compileOnly"), VersionOverrides::declared)
        .zip(spotbugs, (overrides, version) -> withExtension(overrides, "spotbugs", version))
        .zip(jacoco, (overrides, version) -> withExtension(overrides, "jacoco", version));
  }

  private static Map<String, String> declared(Configuration errorprone, Configuration compileOnly) {
    Map<String, String> overrides = new HashMap<>();
    put(
        overrides,
        "errorprone",
        declaredVersion(errorprone.getDependencies(), "com.google.errorprone", "error_prone_core"));
    put(
        overrides,
        "nullaway",
        declaredVersion(errorprone.getDependencies(), "com.uber.nullaway", "nullaway"));
    put(
        overrides,
        "jspecify",
        declaredVersion(compileOnly.getDependencies(), "org.jspecify", "jspecify"));
    return overrides;
  }

  private static Map<String, String> withExtension(
      Map<String, String> overrides, String tool, String version) {
    Map<String, String> result = new HashMap<>(overrides);
    put(result, tool, extensionOverride(tool, version));
    return Map.copyOf(result);
  }

  private static void put(Map<String, String> overrides, String tool, String version) {
    if (version != null) {
      overrides.put(tool, version);
    }
  }

  /**
   * The version a project declared for a module, if it declared one.
   *
   * @param dependencies a configuration's declared dependencies
   * @param group the module's group
   * @param name the module's name
   * @return the declared version, {@link #MANAGED} when declared without one, or null when the
   *     module isn't declared
   */
  static String declaredVersion(Collection<Dependency> dependencies, String group, String name) {
    for (Dependency dependency : dependencies) {
      if (group.equals(dependency.getGroup()) && name.equals(dependency.getName())) {
        String version = dependency.getVersion();
        return version == null || version.isEmpty() ? MANAGED : version;
      }
    }
    return null;
  }

  /**
   * A tool extension's version when it differs from Sterling's pin.
   *
   * @param tool the tool's name in {@link ToolVersions}
   * @param version the extension's {@code toolVersion}
   * @return the version, or null when it's Sterling's pin
   */
  static String extensionOverride(String tool, String version) {
    return version == null || version.equals(ToolVersions.get(tool)) ? null : version;
  }
}
