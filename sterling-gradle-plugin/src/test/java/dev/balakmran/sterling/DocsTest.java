package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;

import dev.balakmran.sterling.internal.SterlingInfoTask;
import java.beans.Introspector;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

/** The docs must document every setting, and their versions must match what Sterling pins. */
class DocsTest {
  private static final Map<String, String> TOOL_NAMES =
      Map.of(
          "google-java-format", "google-java-format",
          "errorprone", "Error Prone",
          "nullaway", "NullAway",
          "jspecify", "JSpecify",
          "spotbugs", "SpotBugs",
          "jacoco", "JaCoCo");

  private final String readme = read(Path.of(System.getProperty("sterling.readme")));
  private final String configuration = guide("configuration.md");
  private final String tools = guide("tools.md");

  @Test
  void everySettingIsInTheSettingsReference() {
    String reference = section(configuration, "### Settings reference");

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
            .matcher(section(tools, "## Pinned tool versions"));
    while (row.find()) {
      table.put(row.group(1), row.group(2));
    }

    assertThat(table).containsExactlyInAnyOrderEntriesOf(expected);
  }

  @Test
  void jspecifyCoordinatesUseThePinnedVersion() {
    Matcher coordinate =
        Pattern.compile("org\\.jspecify:jspecify:([0-9][^\"\\s)`]*)").matcher(readme + allGuides());
    List<String> versions = new ArrayList<>();
    while (coordinate.find()) {
      versions.add(coordinate.group(1));
    }

    assertThat(versions).isNotEmpty().containsOnly(ToolVersions.get("jspecify"));
  }

  @Test
  void spotbugsAnnotationsUseThePinnedVersion() {
    Matcher coordinate =
        Pattern.compile("com\\.github\\.spotbugs:spotbugs-annotations:([0-9][^\"\\s)`]*)")
            .matcher(readme + allGuides());
    List<String> versions = new ArrayList<>();
    while (coordinate.find()) {
      versions.add(coordinate.group(1));
    }

    assertThat(versions).isNotEmpty().containsOnly(ToolVersions.get("spotbugs"));
  }

  @Test
  void profileTableMatchesEachProfilesDefaults() {
    Project project = ProjectBuilder.builder().build();
    project.getPluginManager().apply(SterlingPlugin.class);
    SterlingExtension sterling = project.getExtensions().getByType(SterlingExtension.class);
    // Rows like: | `coverage.branch` | 0 | 70 | 85 |, in LENIENT, STANDARD, STRICT order.
    Matcher row =
        Pattern.compile("(?m)^\\| `([a-zA-Z.]+)` \\| ([^|]+) \\| ([^|]+) \\| ([^|]+) \\|$")
            .matcher(section(configuration, "## Profile defaults"));
    List<String> checked = new ArrayList<>();
    while (row.find()) {
      String setting = row.group(1);
      for (Profile profile : Profile.values()) {
        sterling.getProfile().set(profile);
        assertThat(row.group(profile.ordinal() + 2).trim())
            .describedAs(setting + " in " + profile)
            .isEqualTo(asDocumented(value(sterling, setting)));
      }
      checked.add(setting);
    }

    assertThat(checked).isNotEmpty();
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

  // Each setting as the settings reference writes it: "coverage.instruction",
  // "errorProne.disable(...)".
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

  // A setting's value through its getters: "coverage.branch" is getCoverage().getBranch().get().
  private static Object value(Object target, String setting) {
    Object current = target;
    for (String part : setting.split("\\.")) {
      try {
        current =
            current
                .getClass()
                .getMethod("get" + Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .invoke(current);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("No setting " + setting, e);
      }
    }
    return ((org.gradle.api.provider.Provider<?>) current).get();
  }

  // How the table writes a value: enum constants in backticks, everything else as is.
  private static String asDocumented(Object value) {
    return value instanceof Enum<?> constant ? "`" + constant.name() + "`" : String.valueOf(value);
  }

  private static String section(String doc, String heading) {
    int start = doc.indexOf("\n" + heading + "\n");
    assertThat(start).describedAs(heading).isNotNegative();
    int end = doc.indexOf("\n#", start + heading.length() + 2);
    return doc.substring(start, end < 0 ? doc.length() : end);
  }

  private static String guide(String name) {
    return read(Path.of(System.getProperty("sterling.guide")).resolve(name));
  }

  private static String allGuides() {
    try (Stream<Path> pages = Files.list(Path.of(System.getProperty("sterling.guide")))) {
      return pages.sorted().map(DocsTest::read).collect(Collectors.joining("\n"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
