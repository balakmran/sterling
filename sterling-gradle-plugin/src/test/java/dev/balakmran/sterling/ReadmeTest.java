package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;

import dev.balakmran.sterling.internal.SterlingInfoTask;
import java.beans.Introspector;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

/** The README must document every setting, and its versions must match what Sterling pins. */
class ReadmeTest {
  private static final Map<String, String> TOOL_NAMES =
      Map.of(
          "google-java-format", "google-java-format",
          "errorprone", "Error Prone",
          "nullaway", "NullAway",
          "jspecify", "JSpecify",
          "spotbugs", "SpotBugs",
          "jacoco", "JaCoCo");

  private final String readme = readReadme();

  @Test
  void everySettingIsInTheSettingsReference() {
    String reference = section("### Settings reference");

    assertThat(settings())
        .isNotEmpty()
        .allSatisfy(setting -> assertThat(reference).contains(setting));
  }

  @Test
  void pinnedVersionsTableMatchesTheVersionResource() {
    Map<String, String> expected = new LinkedHashMap<>();
    TOOL_NAMES.forEach((key, name) -> expected.put(name, ToolVersions.get(key)));

    Map<String, String> table = new LinkedHashMap<>();
    Matcher row =
        Pattern.compile("(?m)^\\| ([^|]+?) \\| ([0-9][^|\\s]*) \\|$")
            .matcher(section("## Pinned tool versions"));
    while (row.find()) {
      table.put(row.group(1), row.group(2));
    }

    assertThat(table).containsExactlyInAnyOrderEntriesOf(expected);
  }

  @Test
  void jspecifyCoordinatesUseThePinnedVersion() {
    Matcher coordinate =
        Pattern.compile("org\\.jspecify:jspecify:([0-9][^\"\\s)`]*)").matcher(readme);
    List<String> versions = new ArrayList<>();
    while (coordinate.find()) {
      versions.add(coordinate.group(1));
    }

    assertThat(versions).isNotEmpty().containsOnly(ToolVersions.get("jspecify"));
  }

  @Test
  void stlInfoSampleMatchesWhatStlInfoPrints() {
    Project project = ProjectBuilder.builder().build();
    project.getPluginManager().apply(SterlingPlugin.class);
    SterlingExtension sterling = project.getExtensions().getByType(SterlingExtension.class);
    // The sample shows one disabled check, one exclude and a Java 25 toolchain.
    sterling.getErrorProne().disable("UnusedVariable");
    sterling.getCoverage().getExcludes().add("com/example/Application*");
    SterlingInfoTask task =
        project.getTasks().named(SterlingPlugin.INFO, SterlingInfoTask.class).get();
    task.getJavaToolchain().set("25");

    Matcher block = Pattern.compile("(?s)```\\nSterling [^\\n]*\\n(.*?)\\n```").matcher(readme);
    assertThat(block.find()).describedAs("stlInfo sample in README").isTrue();
    String rendered = task.render("25");

    // The first line is Sterling's own release version.
    assertThat(block.group(1)).isEqualTo(rendered.substring(rendered.indexOf('\n') + 1));
  }

  // Each setting as the README writes it: "coverage.instruction", "errorProne.disable(...)".
  private static List<String> settings() {
    List<String> settings = new ArrayList<>();
    for (Method method : SterlingExtension.class.getMethods()) {
      String property = propertyName(method);
      if (property == null) {
        continue;
      }
      Class<?> type = method.getReturnType();
      if (type.getPackage() == SterlingExtension.class.getPackage()) {
        for (Method nested : type.getMethods()) {
          String leaf = propertyName(nested);
          if (leaf != null) {
            settings.add("`" + property + "." + leaf + "`");
          } else if (isOwnAction(nested)) {
            settings.add("`" + property + "." + nested.getName() + "(...)`");
          }
        }
      } else {
        settings.add("`" + property + "`");
      }
    }
    return settings;
  }

  private static String propertyName(Method method) {
    boolean getter =
        method.getName().startsWith("get")
            && method.getParameterCount() == 0
            && method.getDeclaringClass() != Object.class;
    return getter ? Introspector.decapitalize(method.getName().substring(3)) : null;
  }

  private static boolean isOwnAction(Method method) {
    return method.getDeclaringClass().getPackage() == SterlingExtension.class.getPackage()
        && !Modifier.isStatic(method.getModifiers());
  }

  private String section(String heading) {
    int start = readme.indexOf("\n" + heading + "\n");
    assertThat(start).describedAs(heading).isNotNegative();
    int end = readme.indexOf("\n#", start + heading.length() + 2);
    return readme.substring(start, end < 0 ? readme.length() : end);
  }

  private static String readReadme() {
    try {
      return Files.readString(Path.of(System.getProperty("sterling.readme")));
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }
}
