package dev.balakmran.sterling;

import com.diffplug.gradle.spotless.SpotlessExtension;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

/** Spotless with google-java-format; {@code stlFmt} and {@code stlFmtCheck} follow the flag. */
final class Formatting {
  /** Generated sources, wherever a generator puts them under a build directory. */
  static final String GENERATED_SOURCES = "**/build/generated/**";

  private Formatting() {}

  static void configure(Project project, SterlingExtension sterling) {
    project.getPluginManager().apply("com.diffplug.spotless");
    SpotlessExtension spotless = project.getExtensions().getByType(SpotlessExtension.class);
    // Sterling owns `check`; only stlCheck attaches to it.
    spotless.setEnforceCheck(false);
    spotless.java(java -> {
      // The google-java-format step also removes unused imports.
      java.googleJavaFormat(ToolVersions.get("google-java-format"));
      java.formatAnnotations();
      java.targetExclude(GENERATED_SOURCES);
    });

    Provider<Boolean> enabled = sterling.getFormat().getEnabled();
    project
        .getTasks()
        .named(SterlingPlugin.FMT)
        .configure(task -> task.dependsOn(whenOn(enabled, "spotlessApply")));
    project
        .getTasks()
        .named(SterlingPlugin.FMT_CHECK)
        .configure(task -> task.dependsOn(whenOn(enabled, "spotlessCheck")));
  }

  /** Task names to depend on while the flag is on; none when it's off. */
  static Provider<List<String>> whenOn(Provider<Boolean> flag, String... tasks) {
    List<String> names = List.of(tasks);
    return flag.map(on -> on ? names : List.of());
  }
}
