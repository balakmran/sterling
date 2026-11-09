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

/** The Java plugin, {@code dev.balakmran.sterling.java}: wires the tools once java is applied. */
public class SterlingJavaPlugin implements Plugin<Project> {
  static final String TOOLCHAIN_CHECK = "stlToolchainCheck";

  /** Creates the plugin; Gradle calls this. */
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

  // google-java-format runs inside the daemon and needs JDK 21.
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
  }
}
