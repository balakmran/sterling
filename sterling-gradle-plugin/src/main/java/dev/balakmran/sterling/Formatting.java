package dev.balakmran.sterling;

import static dev.balakmran.sterling.Flags.whenOn;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.spotless.LineEnding;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

/**
 * Configures Spotless with google-java-format for {@code stlFmt} and {@code stlFmtCheck}.
 *
 * <p>Spotless is applied and configured whatever {@code format.enabled} says; the flag only decides
 * whether the Sterling tasks depend on Spotless's.
 */
final class Formatting {
  /** Generated sources, wherever a generator puts them under a build directory. */
  static final String GENERATED_SOURCES = "**/build/generated/**";

  // Spotless's message when a format gets a second step of the same name.
  private static final String DUPLICATE_STEP = "Multiple steps with name '";

  private Formatting() {}

  /**
   * Applies and configures Spotless, and connects it to the Sterling tasks.
   *
   * @param project a project with the {@code java} plugin applied
   * @param sterling the project's {@code sterling} extension
   */
  static void configure(Project project, SterlingExtension sterling) {
    project.getPluginManager().apply("com.diffplug.spotless");
    SpotlessExtension spotless = project.getExtensions().getByType(SpotlessExtension.class);
    // Sterling owns `check`; only stlCheck attaches to it.
    spotless.setEnforceCheck(false);
    // Spotless's default reads git settings while configuring, which defeats the configuration
    // cache. Formatting is about layout; keep each file's line endings as they are.
    spotless.setLineEndings(LineEnding.PRESERVE);
    spotless.java(java -> {
      try {
        // The google-java-format step also removes unused imports.
        java.googleJavaFormat(ToolVersions.get("google-java-format"));
        java.formatAnnotations();
      } catch (GradleException e) {
        // Fails here only when the project's step came first, as from a convention plugin.
        if (e.getMessage() != null && e.getMessage().startsWith(DUPLICATE_STEP)) {
          throw new GradleException(duplicateStepMessage(), e);
        }
        throw e;
      }
      java.targetExclude(GENERATED_SOURCES);
    });

    Provider<Boolean> enabled = sterling.getFormat().getEnabled();
    project
        .getTasks()
        .named(SterlingPlugin.FMT)
        .configure(task ->
            task.dependsOn(
                whenOn(
                    project.getProviders(), enabled, project.getTasks().named("spotlessApply"))));
    project
        .getTasks()
        .named(SterlingPlugin.FMT_CHECK)
        .configure(task ->
            task.dependsOn(
                whenOn(
                    project.getProviders(), enabled, project.getTasks().named("spotlessCheck"))));
  }

  /**
   * Why a project can't add Sterling's Spotless steps as well as Sterling, and what to do.
   *
   * @return the message, naming the version Sterling pins
   */
  static String duplicateStepMessage() {
    return "Sterling adds google-java-format "
        + ToolVersions.get("google-java-format")
        + " and formatAnnotations() to Spotless itself. Remove both from the project's"
        + " spotless { java { } } block; its other steps keep working.";
  }
}
