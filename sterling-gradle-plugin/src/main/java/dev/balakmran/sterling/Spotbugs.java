package dev.balakmran.sterling;

import static dev.balakmran.sterling.Flags.whenOn;

import com.github.spotbugs.snom.Confidence;
import com.github.spotbugs.snom.Effort;
import com.github.spotbugs.snom.SpotBugsExtension;
import com.github.spotbugs.snom.SpotBugsTask;
import dev.balakmran.sterling.internal.GeneratedClassesTask;
import dev.balakmran.sterling.internal.SpotbugsFilterTask;
import dev.balakmran.sterling.internal.SpotbugsSummaryTask;
import dev.balakmran.sterling.internal.SterlingInfoTask;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * Configures SpotBugs on the main classes for {@code stlSec}.
 *
 * <p>Sterling applies {@code com.github.spotbugs-base}, which creates no tasks and doesn't attach
 * to {@code check}, and registers {@code spotbugsMain} itself. Test classes aren't analysed. The
 * exclude filter is Sterling's default merged with the project's own, plus every class compiled
 * from generated sources, as listed by {@code stlGeneratedClasses}, written by {@code
 * stlSpotbugsFilter}. When {@code spotbugsMain} runs, {@code stlSecSummary} prints its findings on
 * the console. {@code spotbugs.enabled} decides whether {@code stlSec} runs {@code spotbugsMain};
 * the task itself always exists.
 */
final class Spotbugs {
  static final String TASK = "spotbugsMain";
  static final String FILTER_TASK = "stlSpotbugsFilter";
  static final String SUMMARY_TASK = "stlSecSummary";
  static final String DEFAULT_FILTER = "spotbugs-exclude.xml";

  /**
   * The full SpotBugs plugin, which registers {@code spotbugsMain} and {@code spotbugsTest} and
   * attaches them to {@code check}: both Sterling's job.
   */
  static final String FULL_PLUGIN = "com.github.spotbugs";

  /** Why a project can't apply {@link #FULL_PLUGIN} as well as Sterling, and what to do. */
  static final String FULL_PLUGIN_MESSAGE =
      "Sterling applies SpotBugs itself, with com.github.spotbugs-base, and registers spotbugsMain."
          + " Remove the com.github.spotbugs plugin; spotbugs { } settings keep working.";

  private Spotbugs() {}

  /**
   * Applies and configures SpotBugs, and connects {@code spotbugsMain} to {@code stlSec}.
   *
   * @param project a project with the {@code java} plugin applied
   * @param sterling the project's {@code sterling} extension
   * @param generated {@code stlGeneratedClasses}, whose list the filter excludes
   */
  static void configure(
      Project project, SterlingExtension sterling, TaskProvider<GeneratedClassesTask> generated) {
    project.getPluginManager().apply("com.github.spotbugs-base");
    SpotBugsExtension extension = project.getExtensions().getByType(SpotBugsExtension.class);
    extension.getToolVersion().convention(ToolVersions.get("spotbugs"));
    // Conventions on SpotBugs' own extension, so a project's spotbugs { } or task setting wins.
    SpotbugsSettings settings = sterling.getSpotbugs();
    extension.getEffort().convention(settings.getEffort().map(Spotbugs::effort));
    extension.getReportLevel().convention(settings.getReportLevel().map(Spotbugs::confidence));

    TaskContainer tasks = project.getTasks();
    SourceSet main = project.getExtensions().getByType(SourceSetContainer.class).getByName("main");
    var filter =
        tasks.register(
            FILTER_TASK,
            SpotbugsFilterTask.class,
            task -> {
              task.setGroup(SterlingPlugin.GROUP);
              task.setDescription("Merges Sterling's SpotBugs exclude filter with the project's.");
              task.getSterlingFilter().set(defaultFilter());
              task.getConsumerFilter().set(sterling.getSpotbugs().getExcludeFilter());
              task.getGeneratedClasses().set(generated.flatMap(GeneratedClassesTask::getClassList));
              task.getMergedFilter()
                  .set(
                      project
                          .getLayout()
                          .getBuildDirectory()
                          .file("sterling/spotbugs-exclude.xml"));
            });

    var spotbugsMain =
        tasks.register(
            TASK,
            SpotBugsTask.class,
            task -> {
              // SpotBugs' own group for it, so the sterling group lists only stl* tasks.
              task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
              task.setDescription("Analyses the main classes with SpotBugs.");
              task.getClassDirs().from(main.getOutput().getClassesDirs());
              task.getSourceDirs().from(main.getAllSource().getSourceDirectories());
              task.getAuxClassPaths().from(main.getCompileClasspath());
              task.getExcludeFilter().set(filter.flatMap(SpotbugsFilterTask::getMergedFilter));
              for (String format : List.of("html", "xml", "sarif")) {
                task.getReports().create(format, report -> report.getRequired().set(true));
              }
            });

    for (String format : List.of("xml", "sarif")) {
      SterlingPlugin.reportToInfo(
          project,
          SterlingInfoTask::getSpotbugsReports,
          format,
          spotbugsMain.flatMap(task -> task.getReports().getByName(format).getOutputLocation()));
    }

    var summary =
        tasks.register(
            SUMMARY_TASK,
            SpotbugsSummaryTask.class,
            task -> {
              // Internal: no group, so tasks lists only the stl* tasks people run.
              task.setDescription("Prints spotbugsMain's findings on the console.");
              task.onlyIf("the XML report exists", SpotbugsSummaryTask.reportExists());
              // No task dependency: spotbugsMain failing must not stop its summary.
              task.getXmlReport().set(project.provider(() -> report(spotbugsMain, "xml")));
              task.getHtmlReport().set(project.provider(() -> report(spotbugsMain, "html")));
              task.mustRunAfter(spotbugsMain);
              task.getSourceDirectories().from(main.getJava().getSourceDirectories());
              task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
            });
    spotbugsMain.configure(task ->
        task.finalizedBy(
            whenOn(project.getProviders(), sterling.getSpotbugs().getEnabled(), summary)));

    tasks
        .named(SterlingPlugin.SEC)
        .configure(task ->
            task.dependsOn(
                whenOn(project.getProviders(), sterling.getSpotbugs().getEnabled(), spotbugsMain)));
  }

  private static RegularFile report(TaskProvider<SpotBugsTask> task, String format) {
    return task.get().getReports().getByName(format).getOutputLocation().get();
  }

  /**
   * SpotBugs' effort level for Sterling's.
   *
   * @param effort Sterling's effort level
   * @return the SpotBugs plugin's level of the same name
   */
  static Effort effort(SpotbugsSettings.Effort effort) {
    return Effort.valueOf(effort.name());
  }

  /**
   * SpotBugs' report level for Sterling's.
   *
   * @param confidence Sterling's report level
   * @return the SpotBugs plugin's level of the same name
   */
  static Confidence confidence(SpotbugsSettings.Confidence confidence) {
    return Confidence.valueOf(confidence.name());
  }

  /**
   * Reads Sterling's default exclude filter from the plugin jar.
   *
   * @return the filter's XML
   */
  static String defaultFilter() {
    try (InputStream in = Spotbugs.class.getResourceAsStream(DEFAULT_FILTER)) {
      if (in == null) {
        throw new IllegalStateException(
            "Sterling's " + DEFAULT_FILTER + " is missing from the jar.");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
