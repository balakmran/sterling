package dev.balakmran.sterling;

import static dev.balakmran.sterling.internal.ToolchainCheckTask.MIN_JAVA;

import dev.balakmran.sterling.internal.SterlingInfoTask;
import dev.balakmran.sterling.internal.ToolchainCheckTask;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * The Sterling plugin, {@code dev.balakmran.sterling}.
 *
 * <p>When applied, it:
 *
 * <ol>
 *   <li>fails straight away if Gradle itself runs on a JDK below 21, because google-java-format
 *       runs inside the Gradle daemon;
 *   <li>applies Gradle's {@code base} plugin, creates the {@code sterling} extension with each
 *       setting's profile default, and registers the tasks in the {@code sterling} group;
 *   <li>once the {@code java} plugin is applied, before or after Sterling, applies and configures
 *       Spotless, Error Prone, JaCoCo and SpotBugs, connects them to the {@code stl*} tasks, and
 *       adds {@code stlToolchainCheck}, which fails {@code compileJava} on a toolchain below Java
 *       21.
 * </ol>
 *
 * <p>The tasks:
 *
 * <ul>
 *   <li>{@code stlFmt} formats sources; it's the only task that changes them.
 *   <li>{@code stlFmtCheck}, {@code stlLint}, {@code stlSec} and {@code stlCov} verify formatting,
 *       compile-time analysis, bytecode analysis and coverage.
 *   <li>{@code stlCheck} runs those four, and is the task {@code check} depends on.
 *   <li>{@code stlInfo} prints the resolved configuration.
 * </ul>
 *
 * <p>Without {@code java}, the tools stay inactive, the tasks have nothing to run and nothing
 * fails. Sterling never applies {@code java} itself.
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
  static final String TOOLCHAIN_CHECK = "stlToolchainCheck";
  static final String JAVA_NOT_APPLIED = "not active (java plugin not applied)";

  /** Creates the plugin. Gradle instantiates it when a build applies the plugin ID. */
  public SterlingPlugin() {}

  @Override
  public void apply(Project project) {
    checkDaemonJvm(JavaVersion.current());
    project.getPluginManager().apply(BasePlugin.class);
    SterlingExtension sterling = project.getExtensions().create(EXTENSION, SterlingExtension.class);
    applyConventions(sterling, project.getProviders());
    var stlCheck = registerLifecycleTasks(project.getTasks());
    registerInfoTask(project.getTasks(), sterling);
    // Sterling owns check: only the gate attaches to it, and only while attachToCheck is on.
    project
        .getTasks()
        .named(LifecycleBasePlugin.CHECK_TASK_NAME)
        .configure(check ->
            check.dependsOn(
                Flags.whenOn(project.getProviders(), sterling.getAttachToCheck(), stlCheck)));
    project.getPluginManager().withPlugin("java", java -> configureJava(project, sterling));
  }

  /**
   * Fails when the Gradle daemon runs on a JDK below Sterling's floor.
   *
   * <p>google-java-format runs inside the daemon and needs JDK 21. The message tells the user how
   * to move the daemon to a newer JDK.
   *
   * @param current the daemon's Java version
   * @throws GradleException if {@code current} is below Java 21
   */
  static void checkDaemonJvm(JavaVersion current) {
    if (Integer.parseInt(current.getMajorVersion()) < MIN_JAVA) {
      throw new GradleException(
          "Sterling requires Gradle to run on JDK "
              + MIN_JAVA
              + "+ (found "
              + current.getMajorVersion()
              + "). Run ./gradlew updateDaemonJvm --jvm-version="
              + MIN_JAVA
              + ", or point JAVA_HOME at JDK "
              + MIN_JAVA
              + "+.");
    }
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
  private static TaskProvider<Task> registerLifecycleTasks(TaskContainer tasks) {
    lifecycle(tasks, FMT, "Formats Java sources with google-java-format.");
    var fmtCheck = lifecycle(tasks, FMT_CHECK, "Verifies Java sources are formatted.");
    var lint = lifecycle(tasks, LINT, "Compiles Java sources with Error Prone and NullAway.");
    var sec = lifecycle(tasks, SEC, "Analyses bytecode with SpotBugs.");
    var cov = lifecycle(tasks, COV, "Verifies test coverage against the thresholds.");
    var check = lifecycle(tasks, CHECK, "Runs every enabled Sterling verification.");
    check.configure(task -> task.dependsOn(fmtCheck, lint, sec, cov));
    return check;
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

  private static void configureJava(Project project, SterlingExtension sterling) {
    var compileJava = project.getTasks().named("compileJava", JavaCompile.class);
    Provider<Integer> toolchainVersion =
        compileJava
            .flatMap(JavaCompile::getJavaCompiler)
            .map(compiler -> compiler.getMetadata().getLanguageVersion().asInt());

    var toolchainCheck =
        project
            .getTasks()
            .register(
                TOOLCHAIN_CHECK,
                ToolchainCheckTask.class,
                task -> {
                  task.setGroup(GROUP);
                  task.setDescription("Fails when the Java toolchain is below " + MIN_JAVA + ".");
                  task.getLanguageVersion().set(toolchainVersion);
                  task.getMarker()
                      .set(
                          project
                              .getLayout()
                              .getBuildDirectory()
                              .file("sterling/toolchain-check.txt"));
                });
    compileJava.configure(task -> task.dependsOn(toolchainCheck));

    project
        .getTasks()
        .named(INFO, SterlingInfoTask.class)
        .configure(task -> task.getJavaToolchain().set(toolchainVersion.map(String::valueOf)));

    Formatting.configure(project, sterling);
    Coverage.configure(project, sterling);
    Spotbugs.configure(project, sterling);
    ErrorProne.configure(project, sterling);
  }
}
