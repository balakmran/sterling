package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The agent plugin under agent-plugins/sterling: its manifests, skills and scripts. */
class AgentPluginTest {
  // The Agent Plugins 1.0 manifest's closed set of top-level fields, as the vendored schema lists.
  private static final List<String> AGENT_PLUGINS_FIELDS =
      List.of(
          "$schema",
          "name",
          "version",
          "description",
          "author",
          "homepage",
          "repository",
          "license",
          "keywords",
          "extensions");
  private static final Pattern SKILL_NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
  // A Claude Code rule for one of the skill's own scripts: Bash(${CLAUDE_SKILL_DIR}/scripts/x.sh).
  private static final Pattern SCRIPT_RULE =
      Pattern.compile("Bash\\(\\$\\{CLAUDE_SKILL_DIR}/scripts/([a-z0-9.-]+)(:\\*)?\\)");
  private static final Set<String> TASKS =
      Set.of(
          SterlingPlugin.FMT,
          SterlingPlugin.FIX,
          SterlingPlugin.FMT_CHECK,
          SterlingPlugin.LINT,
          SterlingPlugin.SEC,
          SterlingPlugin.COV,
          SterlingPlugin.CHECK,
          SterlingPlugin.INFO);

  private final Path root = Path.of(System.getProperty("sterling.agentPlugins"));
  private final Path plugin = root.resolve("sterling");
  private final String version = read(Path.of(System.getProperty("sterling.versionFile"))).trim();

  @Test
  void everyManifestIsNamedSterlingAtSterlingsVersion() {
    for (String manifest :
        List.of("plugin.json", ".claude-plugin/plugin.json", ".codex-plugin/plugin.json")) {
      String json = read(plugin.resolve(manifest));
      assertThat(field(json, "name")).describedAs(manifest).isEqualTo("sterling");
      assertThat(field(json, "version")).describedAs(manifest).isEqualTo(version);
    }
  }

  @Test
  void theRootManifestUsesOnlyAgentPluginsFieldsAndNoOpenAiExtension() {
    String json = read(plugin.resolve("plugin.json"));
    String schema = read(root.resolve("schemas/agent-plugins-1.0.0-plugin.schema.json"));

    assertThat(field(json, "$schema"))
        .isEqualTo("https://agent-plugins.org/schemas/1.0.0/plugin.schema.json");
    assertThat(topLevelKeys(json)).isSubsetOf(AGENT_PLUGINS_FIELDS);
    AGENT_PLUGINS_FIELDS.forEach(field -> assertThat(schema).contains("\"" + field + "\": {"));
    // With extensions.com.openai present, Codex would ignore .codex-plugin/plugin.json.
    assertThat(json).doesNotContain("com.openai");
  }

  @Test
  void everySkillFollowsTheAgentSkillsSpecAndCarriesSterlingsVersion() {
    List<Path> skills = skills();
    assertThat(skills).isNotEmpty();
    for (Path skill : skills) {
      Map<String, String> frontmatter = frontmatter(skill.resolve("SKILL.md"));
      String dir = skill.getFileName().toString();

      assertThat(frontmatter.get("name")).describedAs(dir).isEqualTo(dir);
      assertThat(dir).hasSizeLessThanOrEqualTo(64).matches(SKILL_NAME);
      assertThat(frontmatter.get("description"))
          .describedAs(dir + " description")
          .isNotBlank()
          .hasSizeLessThanOrEqualTo(1024);
      assertThat(frontmatter.get("metadata.sterling-version"))
          .describedAs(dir + " metadata.sterling-version")
          .isEqualTo(version);
    }
  }

  @Test
  void allowedToolsPreApproveOnlyTheSkillsOwnScripts() {
    for (Path skill : skills()) {
      String rules = frontmatter(skill.resolve("SKILL.md")).get("allowed-tools");
      if (rules == null) {
        continue;
      }
      for (String rule : rules.trim().split(" +")) {
        Matcher matcher = SCRIPT_RULE.matcher(rule);
        assertThat(matcher.matches()).describedAs(skill.getFileName() + ": " + rule).isTrue();
        assertThat(skill.resolve("scripts").resolve(matcher.group(1)))
            .describedAs(rule)
            .isRegularFile();
      }
    }
  }

  @Test
  void skillsNameOnlyTasksAndSettingsThatExist() {
    Set<String> settings = Set.copyOf(DocsTest.settings());
    Pattern task = Pattern.compile("\\b(stl[A-Z][A-Za-z]*)\\b");
    Pattern setting =
        Pattern.compile("`((?:format|errorProne|nullAway|spotbugs|coverage)\\.[a-zA-Z]+)`");
    for (Path skill : skills()) {
      String text = markdown(skill);
      Matcher tasks = task.matcher(text);
      while (tasks.find()) {
        assertThat(TASKS)
            .describedAs(skill.getFileName() + " names a task")
            .contains(tasks.group(1));
      }
      Matcher used = setting.matcher(text);
      while (used.find()) {
        assertThat(settings)
            .describedAs(skill.getFileName() + " names a setting")
            .anyMatch(known -> known.startsWith("`" + used.group(1)));
      }
    }
  }

  @Test
  void releasePleaseBumpsEveryManifestAndSkill() {
    String config = read(root.getParent().resolve("release-please-config.json"));

    for (String manifest :
        List.of("plugin.json", ".claude-plugin/plugin.json", ".codex-plugin/plugin.json")) {
      assertThat(config).contains("\"agent-plugins/sterling/" + manifest + "\"");
    }
    for (Path skill : skills()) {
      assertThat(config)
          .contains("\"agent-plugins/sterling/skills/" + skill.getFileName() + "/SKILL.md\"");
    }
  }

  @Test
  void scriptsSharedBetweenSkillsAreIdenticalCopies() {
    Map<String, String> first = new HashMap<>();
    for (Path skill : skills()) {
      Path scripts = skill.resolve("scripts");
      if (!Files.isDirectory(scripts)) {
        continue;
      }
      try (Stream<Path> files = Files.list(scripts)) {
        files.forEach(file -> {
          String name = file.getFileName().toString();
          String content = read(file);
          String earlier = first.putIfAbsent(name, content);
          assertThat(earlier == null || earlier.equals(content))
              .describedAs(name + " differs between skills")
              .isTrue();
        });
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @Test
  void duplicateConfigListsTheSameItemsAsTheMigrationGuide() {
    Path guide = Path.of(System.getProperty("sterling.guide"));
    List<String> guideItems = firstColumn(read(guide.resolve("migrating.md")), "## What to remove");
    List<String> skillItems =
        firstColumn(
            read(plugin.resolve("skills/adopt-sterling/reference/duplicate-config.md")),
            "| In your build | What to do |");

    assertThat(guideItems).isNotEmpty();
    assertThat(skillItems).containsExactlyElementsOf(guideItems);
  }

  @Test
  void everyShellScriptIsExecutable() throws IOException {
    for (Path skill : skills()) {
      Path scripts = skill.resolve("scripts");
      if (!Files.isDirectory(scripts)) {
        continue;
      }
      try (Stream<Path> files = Files.list(scripts)) {
        for (Path script : files.filter(file -> file.toString().endsWith(".sh")).toList()) {
          assertThat(Files.isExecutable(script)).describedAs(script.toString()).isTrue();
        }
      }
    }
  }

  // The first cell of each table row after the marker line, until the table ends.
  private static List<String> firstColumn(String markdown, String marker) {
    int start = markdown.indexOf(marker);
    assertThat(start).describedAs(marker).isNotNegative();
    List<String> cells = new ArrayList<>();
    boolean inTable = false;
    for (String line : markdown.substring(start).lines().toList()) {
      if (line.startsWith("|---")) {
        inTable = true;
      } else if (inTable && line.startsWith("| ")) {
        cells.add(line.substring(2, line.indexOf(" | ")));
      } else if (inTable) {
        break;
      }
    }
    return cells;
  }

  private List<Path> skills() {
    try (Stream<Path> dirs = Files.list(plugin.resolve("skills"))) {
      // Agents load a folder as a skill only once it has a SKILL.md.
      return dirs.filter(dir -> Files.isRegularFile(dir.resolve("SKILL.md"))).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // A skill's SKILL.md and reference pages.
  private static String markdown(Path skill) {
    try (Stream<Path> files = Files.walk(skill)) {
      return files
          .filter(file -> file.toString().endsWith(".md"))
          .map(AgentPluginTest::read)
          .reduce("", String::concat);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // The YAML frontmatter's scalar values, flattened: "metadata.sterling-version". A folded
  // scalar (">-") is joined into one line.
  static Map<String, String> frontmatter(Path file) {
    List<String> lines = read(file).lines().toList();
    assertThat(lines).describedAs(file + " frontmatter").isNotEmpty().first().isEqualTo("---");
    Map<String, String> values = new LinkedHashMap<>();
    String parent = null;
    String folded = null;
    List<String> foldedLines = new ArrayList<>();
    for (String line : lines.subList(1, lines.size())) {
      if (line.equals("---")) {
        break;
      }
      if (folded != null && line.startsWith("  ")) {
        foldedLines.add(line.trim());
        continue;
      }
      if (folded != null) {
        values.put(folded, String.join(" ", foldedLines));
        folded = null;
        foldedLines.clear();
      }
      Matcher entry = Pattern.compile("^( *)([A-Za-z0-9_.-]+):\\s*(.*)$").matcher(line);
      if (!entry.matches()) {
        continue;
      }
      String key = entry.group(1).isEmpty() ? entry.group(2) : parent + "." + entry.group(2);
      String value = entry.group(3).replaceAll("\\s+#.*$", "");
      if (entry.group(1).isEmpty()) {
        parent = entry.group(2);
      }
      if (value.equals(">-") || value.equals(">")) {
        folded = key;
      } else if (!value.isEmpty()) {
        values.put(key, value.replaceAll("^\"|\"$", ""));
      }
    }
    if (folded != null) {
      values.put(folded, String.join(" ", foldedLines));
    }
    return values;
  }

  // A top-level string field of a flat, pretty-printed manifest.
  private static String field(String json, String name) {
    Matcher matcher =
        Pattern.compile("(?m)^  \"" + Pattern.quote(name) + "\": \"([^\"]*)\"").matcher(json);
    assertThat(matcher.find()).describedAs(name).isTrue();
    return matcher.group(1);
  }

  private static List<String> topLevelKeys(String json) {
    Matcher matcher = Pattern.compile("(?m)^  \"([^\"]+)\":").matcher(json);
    List<String> keys = new ArrayList<>();
    while (matcher.find()) {
      keys.add(matcher.group(1));
    }
    return keys;
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
