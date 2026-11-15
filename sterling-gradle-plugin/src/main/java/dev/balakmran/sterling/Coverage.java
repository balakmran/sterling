package dev.balakmran.sterling;

import static dev.balakmran.sterling.Flags.whenOn;

import dev.balakmran.sterling.internal.GeneratedClassesTask;
import dev.balakmran.sterling.internal.SterlingInfoTask;
import java.io.File;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.gradle.testing.jacoco.tasks.JacocoReportBase;
import org.gradle.testing.jacoco.tasks.rules.JacocoLimit;

/**
 * Configures JaCoCo coverage on the test tasks and its verification for {@code stlCov}.
 *
 * <p>The test tasks that count are {@code coverage.testTasks}, or every {@link Test} task when
 * that's empty. The report and the verification depend on each of them and read each one's
 * execution data.
 *
 * <p>JaCoCo is applied whatever {@code coverage.enabled} says. The flag decides whether the counted
 * test tasks get the agent (other test tasks never do), whether the report follows them, and
 * whether {@code stlCov} runs the verification.
 *
 * <p>The agent switch, the thresholds, the execution data and the excludes are plain values on
 * JaCoCo's side, not lazy properties, so they're set once the task graph is ready. A configuration
 * action would be too early when a build script creates the tasks before its {@code sterling { }}
 * block, as Spring Initializr's {@code tasks.withType<Test> { }} does; it would see the profile's
 * defaults instead of the project's settings.
 *
 * <p>Classes compiled from generated sources are always left out of the report and the
 * verification, through a {@link CoverageExclusion} that reads {@code stlGeneratedClasses}' list.
 * With the {@code org.springframework.boot} plugin applied, so is the application's main class,
 * read from the file Spring Boot's {@code resolveMainClassName} writes.
 */
final class Coverage {
  private static final String REPORT = "jacocoTestReport";
  private static final String VERIFICATION = "jacocoTestCoverageVerification";
  private static final String SPRING_BOOT = "org.springframework.boot";
  // Spring Boot's task, which writes the main class's name to its one output file.
  private static final String RESOLVE_MAIN_CLASS = "resolveMainClassName";

  private Coverage() {}

  /**
   * Applies and configures JaCoCo, and connects its verification to {@code stlCov}.
   *
   * @param project a project with the {@code java} plugin applied
   * @param sterling the project's {@code sterling} extension
   * @param generated {@code stlGeneratedClasses}, whose classes are left out
   */
  static void configure(
      Project project, SterlingExtension sterling, TaskProvider<GeneratedClassesTask> generated) {
    project.getPluginManager().apply(JacocoPlugin.class);
    // A plain field, not a Property: set now so the consumer's script, which runs later, wins.
    project
        .getExtensions()
        .getByType(JacocoPluginExtension.class)
        .setToolVersion(ToolVersions.get("jacoco"));

    CoverageSettings coverage = sterling.getCoverage();
    Provider<Boolean> enabled = coverage.getEnabled();
    TaskContainer tasks = project.getTasks();

    Provider<RegularFile> generatedList = generated.flatMap(GeneratedClassesTask::getClassList);
    // Every build script has run by now. The result is stored in the configuration cache.
    project
        .getGradle()
        .getTaskGraph()
        .whenReady(graph ->
            graph.getAllTasks().stream()
                .filter(task -> task.getProject() == project)
                .forEach(task -> applySettings(project, task, coverage, generatedList)));
    ProviderFactory providers = project.getProviders();
    TaskProvider<JacocoReport> report = tasks.named(REPORT, JacocoReport.class);
    // Each counted test task is followed by the report, including suites registered later.
    tasks
        .withType(Test.class)
        .configureEach(test -> {
          String name = test.getName();
          Provider<Boolean> reported =
              providers.provider(() -> enabled.get() && counts(coverage, name));
          test.finalizedBy(whenOn(providers, reported, report));
        });

    // The verification runs the counted tests; the report only follows them. A report depending
    // on them all would make ./gradlew test run every suite, since it finalizes each one.
    Provider<List<String>> counted = providers.provider(() -> countedTests(tasks, coverage));
    report.configure(task -> {
      task.mustRunAfter(counted);
      // Also covers running jacocoTestReport directly with coverage off.
      task.onlyIf("coverage is enabled", Flags.onlyIf(enabled));
      task.getReports().getXml().getRequired().set(true);
      task.getReports().getHtml().getRequired().set(true);
      declareExclusionInputs(task, generatedList);
    });

    SterlingPlugin.reportToInfo(
        project,
        SterlingInfoTask::getCoverageReports,
        "xml",
        report.flatMap(task -> task.getReports().getXml().getOutputLocation()));

    tasks
        .named(VERIFICATION, JacocoCoverageVerification.class)
        .configure(verification -> {
          verification.dependsOn(counted);
          declareExclusionInputs(verification, generatedList);
        });

    // Only with Spring Boot: without it, nothing about the main class is registered.
    project
        .getPluginManager()
        .withPlugin(
            SPRING_BOOT,
            boot -> {
              for (String name : List.of(REPORT, VERIFICATION)) {
                tasks
                    .named(name)
                    .configure(task ->
                        task.getInputs()
                            .files(tasks.named(RESOLVE_MAIN_CLASS))
                            .withPropertyName("sterlingMainClassName")
                            .withPathSensitivity(PathSensitivity.NONE));
              }
              tasks
                  .named(SterlingPlugin.INFO, SterlingInfoTask.class)
                  .configure(info -> info.getExcludesMainClass().set(true));
            });

    tasks
        .named(SterlingPlugin.COV)
        .configure(task -> task.dependsOn(whenOn(providers, enabled, tasks.named(VERIFICATION))));
  }

  /**
   * The test tasks whose runs count towards coverage: {@code coverage.testTasks}, or every {@link
   * Test} task when it's empty.
   *
   * @param tasks the project's tasks
   * @param coverage the coverage settings
   * @return the task names, in the setting's order or else sorted
   * @throws InvalidUserDataException if the setting names a task that isn't a {@code Test} task
   */
  static List<String> countedTests(TaskContainer tasks, CoverageSettings coverage) {
    SortedSet<String> all = tasks.withType(Test.class).getNames();
    Set<String> chosen = coverage.getTestTasks().get();
    if (chosen.isEmpty()) {
      return List.copyOf(all);
    }
    List<String> unknown = chosen.stream().filter(name -> !all.contains(name)).toList();
    if (!unknown.isEmpty()) {
      throw new InvalidUserDataException(
          "sterling.coverage.testTasks names "
              + String.join(", ", unknown)
              + (unknown.size() == 1 ? ", which isn't a Test task" : ", which aren't Test tasks")
              + ". The Test tasks are: "
              + String.join(", ", all)
              + ".");
    }
    return List.copyOf(chosen);
  }

  /**
   * The test tasks {@code stlInfo} shows: {@code coverage.testTasks} as set, or every {@link Test}
   * task. Unlike {@link #countedTests}, a wrong name doesn't fail, so the diagnostic still prints.
   *
   * @param tasks the project's tasks
   * @param coverage the coverage settings
   * @return the task names
   */
  static List<String> shownTests(TaskContainer tasks, CoverageSettings coverage) {
    Set<String> chosen = coverage.getTestTasks().get();
    return chosen.isEmpty()
        ? List.copyOf(tasks.withType(Test.class).getNames())
        : List.copyOf(chosen);
  }

  // Unvalidated: countedTests fails the build for a wrong name before this matters.
  private static boolean counts(CoverageSettings coverage, String test) {
    Set<String> chosen = coverage.getTestTasks().get();
    return chosen.isEmpty() || chosen.contains(test);
  }

  // The exclusion reads these files, so a change to one must rerun the task.
  private static void declareExclusionInputs(Task task, Provider<RegularFile> generatedList) {
    task.getInputs()
        .file(generatedList)
        .withPropertyName("sterlingGeneratedClasses")
        .withPathSensitivity(PathSensitivity.NONE);
  }

  // The values from sterling { }, for one task about to run.
  private static void applySettings(
      Project project, Task task, CoverageSettings coverage, Provider<RegularFile> generatedList) {
    if (task instanceof Test test) {
      // Coverage off means no agent, so a JDK that JaCoCo doesn't support yet can't break tests.
      test.getExtensions()
          .getByType(JacocoTaskExtension.class)
          .setEnabled(coverage.getEnabled().get() && counts(coverage, test.getName()));
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
      applyExcludes(project, verification, coverage, generatedList);
      applyExecutionData(project.getTasks(), verification, coverage);
    } else if (task instanceof JacocoReport report && REPORT.equals(task.getName())) {
      applyExcludes(project, report, coverage, generatedList);
      applyExecutionData(project.getTasks(), report, coverage);
    }
  }

  private static void minimum(JacocoLimit limit, String counter, int percent) {
    limit.setCounter(counter);
    limit.setValue("COVEREDRATIO");
    limit.setMinimum(BigDecimal.valueOf(percent).movePointLeft(2));
  }

  // Replaces JaCoCo's default, test's data only, with every counted task's.
  private static void applyExecutionData(
      TaskContainer tasks, JacocoReportBase task, CoverageSettings coverage) {
    task.getExecutionData().setFrom();
    task.executionData(
        countedTests(tasks, coverage).stream()
            .map(name -> tasks.named(name).get())
            .toArray(Task[]::new));
  }

  // The same class files for the report and the verification, so their numbers agree.
  private static void applyExcludes(
      Project project,
      JacocoReportBase task,
      CoverageSettings coverage,
      Provider<RegularFile> generatedList) {
    List<String> excludes = coverage.getExcludes().get();
    File mainClassName =
        project.getPluginManager().hasPlugin(SPRING_BOOT)
            ? project
                .getTasks()
                .named(RESOLVE_MAIN_CLASS)
                .get()
                .getOutputs()
                .getFiles()
                .getSingleFile()
            : null;
    var exclusion = new CoverageExclusion(generatedList.get().getAsFile(), mainClassName);
    FileCollection classes =
        project
            .getExtensions()
            .getByType(SourceSetContainer.class)
            .getByName("main")
            .getOutput()
            .getClassesDirs();
    task.getClassDirectories()
        .setFrom(
            classes
                .getAsFileTree()
                .matching(pattern -> {
                  pattern.exclude(excludes);
                  pattern.exclude(exclusion);
                }));
  }
}
