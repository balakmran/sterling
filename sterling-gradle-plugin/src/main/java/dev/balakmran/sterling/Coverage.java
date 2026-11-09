package dev.balakmran.sterling;

import static dev.balakmran.sterling.Flags.whenOn;

import java.math.BigDecimal;
import java.util.List;
import org.gradle.api.Project;
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

/** JaCoCo on the {@code test} task; {@code stlCov} verifies the thresholds while the flag is on. */
final class Coverage {
  private static final String TEST = "test";
  private static final String REPORT = "jacocoTestReport";
  private static final String VERIFICATION = "jacocoTestCoverageVerification";

  private Coverage() {}

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

    // Configuration actions run after the build script, so they see the consumer's values.
    // Coverage off means no agent, so a JDK that JaCoCo doesn't support yet can't break tests.
    tasks
        .withType(Test.class)
        .configureEach(test ->
            test.getExtensions().getByType(JacocoTaskExtension.class).setEnabled(enabled.get()));
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
          applyExcludes(project, report, coverage);
        });

    tasks
        .named(VERIFICATION, JacocoCoverageVerification.class)
        .configure(verification -> {
          verification.dependsOn(TEST);
          int instruction =
              Validation.percent(coverage.getInstruction(), "coverage.instruction").get();
          int branch = Validation.percent(coverage.getBranch(), "coverage.branch").get();
          verification
              .getViolationRules()
              .rule(rule -> {
                rule.limit(limit -> minimum(limit, "INSTRUCTION", instruction));
                rule.limit(limit -> minimum(limit, "BRANCH", branch));
              });
          applyExcludes(project, verification, coverage);
        });

    tasks
        .named(SterlingPlugin.COV)
        .configure(task ->
            task.dependsOn(whenOn(project.getProviders(), enabled, tasks.named(VERIFICATION))));
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
