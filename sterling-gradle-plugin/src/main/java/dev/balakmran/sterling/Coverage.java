package dev.balakmran.sterling;

import static dev.balakmran.sterling.Flags.whenOn;

import java.math.BigDecimal;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.gradle.testing.jacoco.tasks.JacocoReportBase;
import org.gradle.testing.jacoco.tasks.rules.JacocoLimit;

/**
 * Configures JaCoCo coverage on the {@code test} task and its verification for {@code stlCov}.
 *
 * <p>JaCoCo is applied whatever {@code coverage.enabled} says. The flag decides whether test tasks
 * get the agent, whether the report follows {@code test}, and whether {@code stlCov} runs the
 * verification.
 *
 * <p>The agent switch, the thresholds and the excludes are plain values on JaCoCo's side, not lazy
 * properties, so they're set once the task graph is ready. A configuration action would be too
 * early when a build script creates the tasks before its {@code sterling { }} block, as Spring
 * Initializr's {@code tasks.withType<Test> { }} does; it would see the profile's defaults instead
 * of the project's settings.
 */
final class Coverage {
  private static final String TEST = "test";
  private static final String REPORT = "jacocoTestReport";
  private static final String VERIFICATION = "jacocoTestCoverageVerification";

  private Coverage() {}

  /**
   * Applies and configures JaCoCo, and connects its verification to {@code stlCov}.
   *
   * @param project a project with the {@code java} plugin applied
   * @param sterling the project's {@code sterling} extension
   */
  static void configure(Project project, SterlingExtension sterling) {
    project.getPluginManager().apply(JacocoPlugin.class);
    // A plain field, not a Property: set now so the consumer's script, which runs later, wins.
    project
        .getExtensions()
        .getByType(JacocoPluginExtension.class)
        .setToolVersion(ToolVersions.get("jacoco"));

    CoverageSettings coverage = sterling.getCoverage();
    Provider<Boolean> enabled = coverage.getEnabled();
    TaskContainer tasks = project.getTasks();

    // Every build script has run by now. The result is stored in the configuration cache.
    project
        .getGradle()
        .getTaskGraph()
        .whenReady(graph ->
            graph.getAllTasks().stream()
                .filter(task -> task.getProject() == project)
                .forEach(task -> applySettings(project, task, coverage)));
    tasks
        .named(TEST)
        .configure(
            test -> test.finalizedBy(whenOn(project.getProviders(), enabled, tasks.named(REPORT))));

    // JaCoCo's tasks don't depend on the test run that produces their data.
    tasks
        .named(REPORT, JacocoReport.class)
        .configure(report -> {
          report.dependsOn(TEST);
          // Also covers running jacocoTestReport directly with coverage off.
          report.onlyIf("coverage is enabled", Flags.onlyIf(enabled));
          report.getReports().getXml().getRequired().set(true);
          report.getReports().getHtml().getRequired().set(true);
        });

    tasks
        .named(VERIFICATION, JacocoCoverageVerification.class)
        .configure(verification -> verification.dependsOn(TEST));

    tasks
        .named(SterlingPlugin.COV)
        .configure(task ->
            task.dependsOn(whenOn(project.getProviders(), enabled, tasks.named(VERIFICATION))));
  }

  // The values from sterling { }, for one task about to run.
  private static void applySettings(Project project, Task task, CoverageSettings coverage) {
    if (task instanceof Test test) {
      // Coverage off means no agent, so a JDK that JaCoCo doesn't support yet can't break tests.
      test.getExtensions()
          .getByType(JacocoTaskExtension.class)
          .setEnabled(coverage.getEnabled().get());
    } else if (task instanceof JacocoCoverageVerification verification
        && VERIFICATION.equals(task.getName())) {
      int instruction = Validation.percent(coverage.getInstruction(), "coverage.instruction").get();
      int branch = Validation.percent(coverage.getBranch(), "coverage.branch").get();
      verification
          .getViolationRules()
          .rule(rule -> {
            rule.limit(limit -> minimum(limit, "INSTRUCTION", instruction));
            rule.limit(limit -> minimum(limit, "BRANCH", branch));
          });
      applyExcludes(project, verification, coverage);
    } else if (task instanceof JacocoReport report && REPORT.equals(task.getName())) {
      applyExcludes(project, report, coverage);
    }
  }

  private static void minimum(JacocoLimit limit, String counter, int percent) {
    limit.setCounter(counter);
    limit.setValue("COVEREDRATIO");
    limit.setMinimum(BigDecimal.valueOf(percent).movePointLeft(2));
  }

  // The same class files for the report and the verification, so their numbers agree.
  private static void applyExcludes(
      Project project, JacocoReportBase task, CoverageSettings coverage) {
    List<String> excludes = coverage.getExcludes().get();
    if (excludes.isEmpty()) {
      return;
    }
    FileCollection classes =
        project
            .getExtensions()
            .getByType(SourceSetContainer.class)
            .getByName("main")
            .getOutput()
            .getClassesDirs();
    task.getClassDirectories()
        .setFrom(classes.getAsFileTree().matching(pattern -> pattern.exclude(excludes)));
  }
}
