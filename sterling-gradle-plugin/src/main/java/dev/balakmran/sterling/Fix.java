package dev.balakmran.sterling;

import com.diffplug.gradle.spotless.SpotlessTask;
import dev.balakmran.sterling.internal.NoWarn;
import dev.balakmran.sterling.internal.PatchChecks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.ltgt.gradle.errorprone.CheckSeverity;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * Configures {@code stlFix}: Error Prone's fixes applied to the sources, then {@code stlFmt}.
 *
 * <p>{@code stlFix} depends on every source set's {@code JavaCompile} while Error Prone is on, and
 * on {@code stlFmt}, which Spotless runs after the compilations so it formats what they changed. A
 * task either changes sources or verifies them, so {@code stlFix} fails fast when a verification
 * task is in the same build.
 */
final class Fix {
  /** The tasks that verify sources, which can't run in a build that changes them. */
  private static final List<String> VERIFY =
      List.of(
          SterlingPlugin.CHECK,
          SterlingPlugin.LINT,
          SterlingPlugin.SEC,
          SterlingPlugin.COV,
          SterlingPlugin.FMT_CHECK,
          LifecycleBasePlugin.CHECK_TASK_NAME);

  private Fix() {}

  /**
   * Connects {@code stlFix} to the compile tasks and {@code stlFmt}, and guards against verifying
   * in the same build.
   *
   * @param project a project with the {@code java} plugin applied
   * @param sterling the project's {@code sterling} extension
   * @return the compile tasks, one per source set, as they appear
   */
  static List<TaskProvider<JavaCompile>> configure(Project project, SterlingExtension sterling) {
    var tasks = project.getTasks();
    List<TaskProvider<JavaCompile>> compiles = new ArrayList<>();
    project
        .getExtensions()
        .getByType(SourceSetContainer.class)
        .configureEach(sourceSet -> {
          var compile = tasks.named(sourceSet.getCompileJavaTaskName(), JavaCompile.class);
          compiles.add(compile);
        });
    // Ordering only: Spotless formats the sources the compilations just patched. It's the
    // Spotless tasks that format, not the spotlessApply lifecycle task over them.
    tasks.withType(SpotlessTask.class).configureEach(spotless -> spotless.mustRunAfter(compiles));

    Provider<Boolean> enabled = sterling.getErrorProne().getEnabled();
    Provider<List<TaskProvider<JavaCompile>>> patching =
        project.getProviders().provider(() -> enabled.get() ? List.copyOf(compiles) : List.of());
    tasks
        .named(SterlingPlugin.FIX)
        .configure(fix -> fix.dependsOn(patching, tasks.named(SterlingPlugin.FMT)));

    // Only strings are captured: the listener must not hold on to the project.
    var patchChecks = sterling.getErrorProne().getPatchChecks();
    String fixPath = project.absoluteProjectPath(SterlingPlugin.FIX);
    List<String> verifyPaths = VERIFY.stream().map(project::absoluteProjectPath).toList();
    project
        .getGradle()
        .getTaskGraph()
        .whenReady(graph -> {
          if (!graph.hasTask(fixPath)) {
            return;
          }
          List<String> conflicts = verifyPaths.stream().filter(graph::hasTask).toList();
          if (!conflicts.isEmpty()) {
            throw new GradleException(conflictMessage(fixPath, conflicts));
          }
          // The graph is stored after this runs, so a reused configuration cache keeps the
          // patching; the listener itself doesn't run again.
          for (var provider : compiles) {
            JavaCompile compile = provider.get();
            if (graph.hasTask(compile)) {
              patch(compile, patchChecks);
            }
          }
        });
    return compiles;
  }

  /**
   * Makes one compilation patch the sources with Error Prone, never up to date and never cached.
   *
   * <p>The flags are inputs of the task, so a normal compilation afterwards has another cache key
   * and neither patches nor takes the patching run's outputs.
   *
   * @param compile a compilation that {@code stlFix} runs
   * @param patchChecks the {@code errorProne.patchChecks} setting
   */
  private static void patch(JavaCompile compile, Provider<Set<String>> patchChecks) {
    ErrorProneOptions errorProne =
        ((ExtensionAware) compile.getOptions()).getExtensions().getByType(ErrorProneOptions.class);
    if (!errorProne.getEnabled().getOrElse(false)) {
      return;
    }
    Provider<Set<String>> off =
        errorProne
            .getChecks()
            .map(checks ->
                checks.entrySet().stream()
                    .filter(entry -> entry.getValue() == CheckSeverity.OFF)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet()));
    errorProne.getErrorproneArgumentProviders().add(new PatchChecks(patchChecks, off));
    compile.getOptions().getCompilerArgumentProviders().add(new NoWarn());
    compile.getOutputs().upToDateWhen(Specs.satisfyNone());
    compile.getOutputs().doNotCacheIf("stlFix rewrites sources", Specs.satisfyAll());
  }

  /**
   * Why {@code stlFix} can't run beside verification tasks, and what to do.
   *
   * @param fixPath the path of {@code stlFix}
   * @param conflicts the paths of the verification tasks in the same build
   * @return the message, naming each
   */
  static String conflictMessage(String fixPath, List<String> conflicts) {
    return fixPath
        + " changes sources, so it can't run in the same build as "
        + String.join(", ", conflicts)
        + ", which verify them. Run "
        + fixPath
        + " first, then the verification.";
  }
}
