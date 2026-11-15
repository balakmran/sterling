package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;

import dev.balakmran.sterling.internal.SterlingInfoTask;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension;
import org.junit.jupiter.api.Test;

class VersionOverridesTest {
  private final Project project = ProjectBuilder.builder().build();

  VersionOverridesTest() {
    project.getPluginManager().apply("java");
    project.getPluginManager().apply(SterlingPlugin.class);
  }

  @Test
  void noOverridesWhenTheProjectDeclaresNothing() {
    assertThat(VersionOverrides.of(project).get()).isEmpty();
  }

  @Test
  void declaredDependenciesAndExtensionVersionsAreOverrides() {
    var dependencies = project.getDependencies();
    dependencies.add("errorprone", "com.google.errorprone:error_prone_core:9.9.9");
    dependencies.add("errorprone", "com.uber.nullaway:nullaway:8.8.8");
    dependencies.add("compileOnly", "org.jspecify:jspecify");
    project
        .getExtensions()
        .getByType(com.github.spotbugs.snom.SpotBugsExtension.class)
        .getToolVersion()
        .set("7.7.7");
    project.getExtensions().getByType(JacocoPluginExtension.class).setToolVersion("6.6.6");

    assertThat(VersionOverrides.of(project).get())
        .containsOnly(
            org.assertj.core.api.Assertions.entry("errorprone", "9.9.9"),
            org.assertj.core.api.Assertions.entry("nullaway", "8.8.8"),
            org.assertj.core.api.Assertions.entry("jspecify", VersionOverrides.MANAGED),
            org.assertj.core.api.Assertions.entry("spotbugs", "7.7.7"),
            org.assertj.core.api.Assertions.entry("jacoco", "6.6.6"));
  }

  @Test
  void anExtensionSetToSterlingsPinIsNotAnOverride() {
    assertThat(VersionOverrides.extensionOverride("jacoco", ToolVersions.get("jacoco"))).isNull();
    assertThat(VersionOverrides.extensionOverride("jacoco", "0.0.1")).isEqualTo("0.0.1");
  }

  @Test
  void stlInfoMarksEachOverride() {
    project.getDependencies().add("errorprone", "com.google.errorprone:error_prone_core:9.9.9");
    String rendered =
        project.getTasks().named(SterlingPlugin.INFO, SterlingInfoTask.class).get().render("25");

    assertThat(rendered)
        .contains("Error Prone:          on   (9.9.9 (project override); ")
        .contains("NullAway:             on   (" + ToolVersions.get("nullaway") + "; ");
  }
}
