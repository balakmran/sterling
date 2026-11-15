package dev.balakmran.sterling;

import dev.balakmran.sterling.internal.GeneratedClassesTask;
import java.io.File;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * Registers {@code stlGeneratedClasses}, the one list of classes compiled from generated sources.
 *
 * <p>Generated sources are main's Java source directories under a {@code build/generated}
 * directory, the same rule Spotless and Error Prone apply, plus javac's annotation-processor output
 * directory. SpotBugs and coverage both leave these classes out.
 */
final class GeneratedClasses {
  static final String TASK = "stlGeneratedClasses";

  private GeneratedClasses() {}

  /**
   * Registers the task.
   *
   * @param project a project with the {@code java} plugin applied
   * @return the task, whose {@code classList} is the list file
   */
  static TaskProvider<GeneratedClassesTask> register(Project project) {
    SourceSet main = project.getExtensions().getByType(SourceSetContainer.class).getByName("main");
    var compileJava = project.getTasks().named(main.getCompileJavaTaskName(), JavaCompile.class);
    return project
        .getTasks()
        .register(
            TASK,
            GeneratedClassesTask.class,
            task -> {
              // Internal: no group, so tasks lists only the stl* tasks people run.
              task.setDescription("Lists the classes compiled from generated sources.");
              task.getGeneratedSources()
                  .from(
                      project.provider(() ->
                          main.getJava().getSrcDirs().stream()
                              .filter(GeneratedClasses::isGenerated)
                              .toList()));
              // Its output only exists after compileJava, which this provider depends on.
              task.getGeneratedSources()
                  .from(
                      compileJava.flatMap(
                          compile -> compile.getOptions().getGeneratedSourceOutputDirectory()));
              task.getClassList()
                  .set(
                      project
                          .getLayout()
                          .getBuildDirectory()
                          .file("sterling/generated-classes.txt"));
            });
  }

  /**
   * Whether a source directory holds generated code: whether it's under a {@code build/generated}
   * directory, the same rule Spotless and Error Prone apply.
   *
   * @param directory a source directory
   * @return true for generated sources
   */
  static boolean isGenerated(File directory) {
    return (directory.getPath() + File.separator).matches(ErrorProne.GENERATED_PATHS);
  }
}
