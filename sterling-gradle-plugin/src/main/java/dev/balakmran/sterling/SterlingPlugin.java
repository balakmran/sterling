package dev.balakmran.sterling;

import dev.balakmran.sterling.internal.SterlingInfoTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

/**
 * The base plugin, {@code dev.balakmran.sterling}.
 *
 * <p>It applies Gradle's {@code base} plugin, creates the {@code sterling} extension with each
 * setting's profile default, and registers the tasks in the {@code sterling} group:
 *
 * <ul>
 *   <li>{@code stlFmt} formats sources; it's the only task that changes them.
 *   <li>{@code stlFmtCheck}, {@code stlLint}, {@code stlSec} and {@code stlCov} verify formatting,
 *       compile-time analysis, bytecode analysis and coverage.
 *   <li>{@code stlCheck} runs those four, and is the task {@code check} depends on.
 *   <li>{@code stlInfo} prints the resolved configuration.
 * </ul>
 *
 * <p>On its own the tasks have nothing to run. {@link SterlingJavaPlugin} applies this plugin and
 * connects each task to its tool once the {@code java} plugin is applied, so most builds apply
 * {@code dev.balakmran.sterling.java} instead.
 */
public class SterlingPlugin implements Plugin<Project> {
  static final String EXTENSION = "sterling";
  static final String GROUP = "sterling";
  static final String FMT = "stlFmt";
  static final String FMT_CHECK = "stlFmtCheck";
  static final String LINT = "stlLint";
  static final String SEC = "stlSec";
  static final String COV = "stlCov";
  static final String CHECK = "stlCheck";
  static final String INFO = "stlInfo";
  static final String JAVA_NOT_APPLIED = "not active (dev.balakmran.sterling.java not applied)";

  /** Creates the plugin. Gradle instantiates it when a build applies the plugin ID. */
  public SterlingPlugin() {}

  @Override
  public void apply(Project project) {
    project.getPluginManager().apply(BasePlugin.class);
    SterlingExtension sterling = project.getExtensions().create(EXTENSION, SterlingExtension.class);
    applyConventions(sterling, project.getProviders());
    registerLifecycleTasks(project.getTasks());
    registerInfoTask(project.getTasks(), sterling);
  }

  private static void applyConventions(SterlingExtension sterling, ProviderFactory providers) {
    var profile = sterling.getProfile();
    profile.convention(
        providers.gradleProperty(Profile.PROPERTY).map(Profile::parse).orElse(Profile.STANDARD));
    sterling.getAttachToCheck().convention(profile.map(p -> p.attachToCheck));
    sterling.getFormat().getEnabled().convention(true);
    sterling.getErrorProne().getEnabled().convention(true);
    sterling.getErrorProne().getWarningsAsErrors().convention(profile.map(p -> p.warningsAsErrors));
    sterling.getNullAway().getEnabled().convention(true);
    sterling.getSpotbugs().getEnabled().convention(profile.map(p -> p.spotbugs));
    sterling.getCoverage().getEnabled().convention(profile.map(p -> p.coverage));
    sterling.getCoverage().getInstruction().convention(profile.map(p -> p.instruction));
    sterling.getCoverage().getBranch().convention(profile.map(p -> p.branch));
  }

  // Tool configuration adds each task's dependencies; these only define the gate's shape.
  private static void registerLifecycleTasks(TaskContainer tasks) {
    lifecycle(tasks, FMT, "Formats Java sources with google-java-format.");
    var fmtCheck = lifecycle(tasks, FMT_CHECK, "Verifies Java sources are formatted.");
    var lint = lifecycle(tasks, LINT, "Compiles Java sources with Error Prone and NullAway.");
    var sec = lifecycle(tasks, SEC, "Analyses bytecode with SpotBugs.");
    var cov = lifecycle(tasks, COV, "Verifies test coverage against the thresholds.");
    lifecycle(tasks, CHECK, "Runs every enabled Sterling verification.")
        .configure(task -> task.dependsOn(fmtCheck, lint, sec, cov));
  }

  private static TaskProvider<Task> lifecycle(
      TaskContainer tasks, String name, String description) {
    return tasks.register(
        name,
        task -> {
          task.setGroup(GROUP);
          task.setDescription(description);
        });
  }

  private static void registerInfoTask(TaskContainer tasks, SterlingExtension sterling) {
    tasks.register(
        INFO,
        SterlingInfoTask.class,
        task -> {
          task.setGroup(GROUP);
          task.setDescription("Prints Sterling's resolved configuration.");
          task.getToolVersions().set(ToolVersions.all());
          task.getProfile().set(sterling.getProfile());
          task.getAttachToCheck().set(sterling.getAttachToCheck());
          task.getJavaToolchain().convention(JAVA_NOT_APPLIED);
          task.getFormatEnabled().set(sterling.getFormat().getEnabled());
          task.getErrorProneEnabled().set(sterling.getErrorProne().getEnabled());
          task.getWarningsAsErrors().set(sterling.getErrorProne().getWarningsAsErrors());
          task.getDisabledChecks().set(sterling.getErrorProne().getDisabledChecks());
          task.getNullAwayEnabled().set(sterling.getNullAway().getEnabled());
          task.getSpotbugsEnabled().set(sterling.getSpotbugs().getEnabled());
          task.getConsumerExcludeFilter()
              .set(sterling.getSpotbugs().getExcludeFilter().map(file -> true).orElse(false));
          task.getCoverageEnabled().set(sterling.getCoverage().getEnabled());
          var coverage = sterling.getCoverage();
          task.getInstruction()
              .set(Validation.percent(coverage.getInstruction(), "coverage.instruction"));
          task.getBranch().set(Validation.percent(coverage.getBranch(), "coverage.branch"));
          task.getCoverageExcludes().set(coverage.getExcludes());
        });
  }
}
