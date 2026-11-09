package dev.balakmran.sterling;

import static dev.balakmran.sterling.internal.ToolchainCheckTask.MIN_JAVA;

import dev.balakmran.sterling.internal.SterlingInfoTask;
import dev.balakmran.sterling.internal.ToolchainCheckTask;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * The Java plugin, {@code dev.balakmran.sterling.java}: the one most builds apply.
 *
 * <p>When applied, it:
 *
 * <ol>
 *   <li>fails straight away if Gradle itself runs on a JDK below 21, because google-java-format
 *       runs inside the Gradle daemon;
 *   <li>applies {@link SterlingPlugin}, so the extension and tasks always exist;
 *   <li>once the {@code java} plugin is applied, before or after Sterling, applies and configures
 *       Spotless and JaCoCo, connects them to the {@code stl*} tasks, and adds {@code
 *       stlToolchainCheck}, which fails {@code compileJava} on a toolchain below Java 21.
 * </ol>
 *
 * <p>Without {@code java}, the tools stay inactive and nothing fails. Sterling never applies {@code
 * java} itself.
 */
public class SterlingJavaPlugin implements Plugin<Project> {
  static final String TOOLCHAIN_CHECK = "stlToolchainCheck";

  /** Creates the plugin. Gradle instantiates it when a build applies the plugin ID. */
  public SterlingJavaPlugin() {}

  @Override
  public void apply(Project project) {
    checkDaemonJvm(JavaVersion.current());
    project.getPluginManager().apply(SterlingPlugin.class);
    project
        .getTasks()
        .named(SterlingPlugin.INFO, SterlingInfoTask.class)
        .configure(
            task -> task.getJavaToolchain().convention("not active (java plugin not applied)"));
    project.getPluginManager().withPlugin("java", java -> configureJava(project));
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

  private static void configureJava(Project project) {
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
                  task.setGroup(SterlingPlugin.GROUP);
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
        .named(SterlingPlugin.INFO, SterlingInfoTask.class)
        .configure(task -> task.getJavaToolchain().set(toolchainVersion.map(String::valueOf)));

    SterlingExtension sterling = project.getExtensions().getByType(SterlingExtension.class);
    Formatting.configure(project, sterling);
    Coverage.configure(project, sterling);
  }
}
