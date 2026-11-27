package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;

import dev.balakmran.sterling.internal.SterlingInfoTask;
import java.beans.Introspector;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
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
          "spotless", "Spotless",
          "errorprone", "Error Prone",
          "nullaway", "NullAway",
          "jspecify", "JSpecify",
          "spotbugs", "SpotBugs",
          "jacoco", "JaCoCo");

  private static final List<String> TOOLS_IN_JSON =
      List.of("format", "errorProne", "nullAway", "spotbugs", "coverage");

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
  void everyGuidePageAndTheSecurityPolicyIsLinkedFromTheReadme() {
    Path readmeFile = Path.of(System.getProperty("sterling.readme"));

    assertThat(readmeFile.resolveSibling("SECURITY.md")).isRegularFile();
    assertThat(readme)
        .contains("(SECURITY.md)")
        .contains("(docs/guide/ci.md)")
        .contains("(docs/guide/plain-gradle.md)")
        .contains("(docs/guide/compatibility.md)")
        .contains("(docs/guide/ide.md)");
    assertThat(guide("sterling-problem-matcher.json")).contains("\"problemMatcher\"");
  }

  @Test
  void theNewestCompatibilityRowListsTheVersionsSterlingPins() {
    String table = guide("compatibility.md");
    Matcher row = Pattern.compile("(?m)^\\| (\\d+\\.\\d+\\.\\d+) \\|(.+)\\|$").matcher(table);
    assertThat(row.find()).isTrue();

    List<String> cells = Stream.of(row.group(2).split("\\|")).map(String::strip).toList();

    assertThat(cells)
        .containsExactly(
            ToolVersions.get("errorprone"),
            ToolVersions.get("nullaway"),
            ToolVersions.get("jspecify"),
            ToolVersions.get("spotbugs"),
            ToolVersions.get("jacoco"),
            ToolVersions.get("google-java-format"),
            ToolVersions.get("spotless"));
  }

  @Test
  void thePlainGradlePageUsesTheVersionsSterlingPins() {
    assertThat(guide("plain-gradle.md"))
        .contains("googleJavaFormat(\"" + ToolVersions.get("google-java-format") + "\")")
        .contains("com.diffplug.spotless\") version \"" + ToolVersions.get("spotless") + "\"")
        .contains("error_prone_core:" + ToolVersions.get("errorprone"))
        .contains("nullaway:" + ToolVersions.get("nullaway"))
        .contains("org.jspecify:jspecify:" + ToolVersions.get("jspecify"))
        .contains("toolVersion = \"" + ToolVersions.get("spotbugs") + "\"")
        .contains("toolVersion = \"" + ToolVersions.get("jacoco") + "\"");
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
    // The sample is a Spring Boot service with an integration test suite, one disabled check, one
    // exclude and a Java 25 toolchain.
    sterling.getErrorProne().disable("UnusedVariable");
    sterling.getCoverage().getExcludes().add("com/example/Legacy*");
    SterlingInfoTask task =
        project.getTasks().named(SterlingPlugin.INFO, SterlingInfoTask.class).get();
    task.getJavaToolchain().set("25");
    task.getCoverageTestTasks().set(List.of("test", "integrationTest"));
    task.getExcludesMainClass().set(true);

    Matcher block = Pattern.compile("(?s)```\\nSterling [^\\n]*\\n(.*?)\\n```").matcher(readme);
    assertThat(block.find()).describedAs("stlInfo sample in README").isTrue();
    String rendered = task.render("25");

    // The first line is Sterling's own release version.
    assertThat(block.group(1)).isEqualTo(rendered.substring(rendered.indexOf('\n') + 1));
  }

  @Test
  void everyJsonKeyIsDocumented() throws IOException {
    String section = section(configuration, "## Reading the configuration as JSON");
    List<String> keys;
    try (InputStream in = DocsTest.class.getResourceAsStream("stl-info-v1-keys.txt")) {
      assertThat(in).describedAs("stl-info-v1-keys.txt").isNotNull();
      keys =
          new String(in.readAllBytes(), StandardCharsets.UTF_8)
              .lines()
              .filter(line -> !line.isBlank() && !line.startsWith("#"))
              // Each tool's object is documented through its keys.
              .filter(key -> !TOOLS_IN_JSON.contains(key))
              .toList();
    }

    assertThat(keys)
        .isNotEmpty()
        .allSatisfy(key ->
            assertThat(section)
                .describedAs(key)
                .containsPattern("\\| `" + Pattern.quote(key) + "` \\|"));
  }

  @Test
  void migratingCoversSpringsSnippetAndAHandWrittenBuildLogic() {
    String migrating = guide("migrating.md");

    assertThat(migrating)
        .contains("## What to remove")
        .contains("## From Spring's NullAway snippet")
        .contains("## From a hand-written build-logic")
        .contains("io.spring.nullability")
        .contains("disableAllChecks")
        .contains("RequireExplicitNullMarking")
        .contains("JSpecify mode");
    // The checklist moved out of adopting.md, which points to it.
    assertThat(guide("adopting.md"))
        .contains("(migrating.md)")
        .doesNotContain("`removeUnusedImports()`");
  }

  @Test
  void upgradingHasASectionForTheCurrentMinorRelease() {
    String upgrading = guide("upgrading.md");
    Matcher heading =
        Pattern.compile("(?m)^## (\\d+)\\.(\\d+)\\.\\d+ to (\\d+)\\.(\\d+)\\.0$")
            .matcher(upgrading);
    assertThat(heading.find()).describedAs("a '## X to Y' section").isTrue();
    // Newest first: the first section's target is the newest release with notes.
    int[] newest = {Integer.parseInt(heading.group(3)), Integer.parseInt(heading.group(4))};
    String[] current =
        read(Path.of(System.getProperty("sterling.versionFile"))).trim().split("\\.");
    int[] released = {Integer.parseInt(current[0]), Integer.parseInt(current[1])};

    assertThat(newest[0] * 1000 + newest[1])
        .describedAs(
            "newest section %d.%d vs version %s", newest[0], newest[1], String.join(".", current))
        .isGreaterThanOrEqualTo(released[0] * 1000 + released[1]);
    assertThat(section(upgrading, heading.group(0))).contains("Can add findings");
    assertThat(section(upgrading, "## 0.1.0 to 0.2.0"))
        .contains("CheckContracts")
        .contains("HandleTestAssertionLibraries")
        .contains("Application*")
        .contains("google-java-format is unchanged");
  }

  @Test
  void guideLinksPointToPagesAndHeadingsThatExist() throws IOException {
    Path guide = Path.of(System.getProperty("sterling.guide"));
    Path readmeFile = Path.of(System.getProperty("sterling.readme"));
    List<Path> pages = new ArrayList<>(List.of(readmeFile));
    try (Stream<Path> files = Files.list(guide)) {
      files.sorted().forEach(pages::add);
    }
    Pattern link = Pattern.compile("\\]\\(([^)#:\\s]*\\.md)?(?:#([^)\\s]+))?\\)");
    for (Path page : pages) {
      Matcher matcher = link.matcher(withoutCodeBlocks(read(page)));
      while (matcher.find()) {
        Path target =
            matcher.group(1) == null
                ? page
                : page.getParent().resolve(matcher.group(1)).normalize();
        if (matcher.group(1) == null && matcher.group(2) == null) {
          continue;
        }
        assertThat(target)
            .describedAs(page.getFileName() + " -> " + matcher.group())
            .isRegularFile();
        if (matcher.group(2) != null) {
          assertThat(anchors(read(target)))
              .describedAs(page.getFileName() + " -> " + matcher.group())
              .contains(matcher.group(2));
        }
      }
    }
  }

  // GitHub's heading anchors: lower case, punctuation dropped, spaces as hyphens.
  private static List<String> anchors(String markdown) {
    return withoutCodeBlocks(markdown)
        .lines()
        .filter(line -> line.startsWith("#"))
        .map(line -> line.replaceFirst("^#+\\s*", "").toLowerCase(java.util.Locale.ROOT))
        .map(text -> text.replaceAll("[^a-z0-9 _-]", "").replace(' ', '-'))
        .toList();
  }

  private static String withoutCodeBlocks(String markdown) {
    return markdown.replaceAll("(?s)```.*?```", "");
  }

  // Each setting as the settings reference writes it: "coverage.instruction",
  // "errorProne.disable(...)".
  static List<String> settings() {
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
