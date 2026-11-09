package dev.balakmran.sterling;

import static dev.balakmran.sterling.Flags.whenOn;

import dev.balakmran.sterling.internal.WarningsAsErrors;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.ltgt.gradle.errorprone.CheckSeverity;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * Configures Error Prone on every {@code JavaCompile} task, and {@code stlLint} to run them.
 *
 * <p>Sterling applies {@code net.ltgt.errorprone}, which runs Error Prone inside javac and adds the
 * javac flags it needs. {@code errorProne.enabled} turns it on or off per compilation and decides
 * whether {@code error_prone_core} is downloaded at all. Code under {@code build/generated} is
 * skipped, whichever generator wrote it.
 */
final class ErrorProne {
  /**
   * Paths Error Prone skips: anything under a {@code build/generated} directory. Either separator,
   * so it also works on Windows.
   */
  static final String GENERATED_PATHS = ".*[/\\\\]build[/\\\\]generated[/\\\\].*";

  private static final String GROUP = "com.google.errorprone";
  private static final String CORE = "error_prone_core";

  private ErrorProne() {}

  /**
   * Applies and configures Error Prone, and connects compilation to {@code stlLint}.
   *
   * @param project a project with the {@code java} plugin applied
   * @param sterling the project's {@code sterling} extension
   */
  static void configure(Project project, SterlingExtension sterling) {
    project.getPluginManager().apply("net.ltgt.errorprone");
    ErrorProneSettings settings = sterling.getErrorProne();
    Provider<Boolean> enabled = settings.getEnabled();

    // Created now: the action below runs at resolution time, when Project mustn't be touched.
    Dependency core =
        project.getDependencies().create(GROUP + ":" + CORE + ":" + ToolVersions.get("errorprone"));
    project
        .getConfigurations()
        .named("errorprone")
        .configure(errorprone ->
            errorprone.withDependencies(dependencies -> {
              boolean declared =
                  dependencies.stream()
                      .anyMatch(d -> GROUP.equals(d.getGroup()) && CORE.equals(d.getName()));
              if (enabled.get() && !declared) {
                dependencies.add(core.copy());
              }
            }));

    Provider<Map<String, CheckSeverity>> disabled =
        settings.getDisabledChecks().map(ErrorProne::off);
    project
        .getTasks()
        .withType(JavaCompile.class)
        .configureEach(compile -> {
          ErrorProneOptions options =
              ((ExtensionAware) compile.getOptions())
                  .getExtensions()
                  .getByType(ErrorProneOptions.class);
          // Conventions, so a project's own options.errorprone settings win.
          options.getEnabled().convention(enabled);
          options.getDisableWarningsInGeneratedCode().convention(true);
          options.getExcludedPaths().convention(GENERATED_PATHS);
          options.getChecks().putAll(disabled);
          compile
              .getOptions()
              .getCompilerArgumentProviders()
              .add(new WarningsAsErrors(settings.getWarningsAsErrors()));
        });

    var tasks = project.getTasks();
    tasks
        .named(SterlingPlugin.LINT)
        .configure(task ->
            task.dependsOn(
                whenOn(
                    project.getProviders(),
                    enabled,
                    tasks.named("compileJava"),
                    tasks.named("compileTestJava"))));
  }

  /**
   * The checks a set of names turns off, in the form Error Prone's options take.
   *
   * @param checks the check names
   * @return each name mapped to {@link CheckSeverity#OFF}
   */
  static Map<String, CheckSeverity> off(Set<String> checks) {
    return checks.stream()
        .collect(Collectors.toMap(Function.identity(), check -> CheckSeverity.OFF));
  }
}
