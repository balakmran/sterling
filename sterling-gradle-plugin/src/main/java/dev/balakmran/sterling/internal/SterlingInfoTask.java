package dev.balakmran.sterling.internal;

import dev.balakmran.sterling.FormatSettings;
import dev.balakmran.sterling.Profile;
import dev.balakmran.sterling.SpotbugsSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.options.Option;

/**
 * The {@code stlInfo} task: prints Sterling's resolved configuration.
 *
 * <p>Each input is wired from the {@code sterling} extension when the task is registered, so the
 * output shows the values after the profile, the {@code sterling.profile} property and the build
 * script have all been applied. It never stays up to date, since printing is its only effect.
 *
 * <p>It shows Sterling's settings only. A value set through a tool's own DSL, such as {@code
 * spotbugs { effort = ... }} or {@code options.errorprone { }}, still applies but isn't shown. The
 * exception is a tool version: one the project chose instead of Sterling's pin is shown with
 * "(project override)".
 *
 * <p>With {@code --json}, it prints the same configuration as one line of JSON per project, for
 * scripts and coding agents. The JSON's {@code schemaVersion} is {@value #SCHEMA_VERSION}: adding a
 * key keeps it, while removing or renaming one, or changing a value's type, raises it.
 */
@UntrackedTask(because = "It only prints the resolved configuration.")
public abstract class SterlingInfoTask extends DefaultTask {
  /** The version of the JSON format {@code --json} prints. */
  public static final int SCHEMA_VERSION = 1;

  // The toolchain line's prefix when the Java tools aren't active.
  private static final String NOT_ACTIVE = "not active (";

  /**
   * The version {@link #getVersionOverrides()} holds for a tool the project declared without a
   * version, such as one a platform manages. The text form shows it as is; JSON gives {@code null}.
   */
  public static final String MANAGED = "managed";

  /** Creates the task. Sterling registers it as {@code stlInfo}; Gradle instantiates it. */
  public SterlingInfoTask() {}

  /**
   * Whether to print JSON instead of text. Off by default; {@code --json} turns it on.
   *
   * @return the JSON switch
   */
  @Input
  @Option(option = "json", description = "Prints the configuration as one line of JSON.")
  public abstract Property<Boolean> getJson();

  /**
   * The project's path, such as {@code :api}.
   *
   * @return the project path
   */
  @Input
  public abstract Property<String> getProjectPath();

  /**
   * The {@code sterling.profile} Gradle property as given, if set.
   *
   * @return the raw property value
   */
  @Input
  @Optional
  public abstract Property<String> getProfileProperty();

  /**
   * The tool versions Sterling pins, keyed by tool name, plus {@code sterling} itself.
   *
   * @return the tool versions
   */
  @Input
  public abstract MapProperty<String, String> getToolVersions();

  /**
   * The tool versions the project chose instead of Sterling's pins, keyed like {@link
   * #getToolVersions()}. Each is shown with "(project override)".
   *
   * @return the overridden versions
   */
  @Input
  public abstract MapProperty<String, String> getVersionOverrides();

  /**
   * The resolved profile.
   *
   * @return the profile
   */
  @Input
  public abstract Property<Profile> getProfile();

  /**
   * Whether {@code check} depends on {@code stlCheck}.
   *
   * @return whether the gate is attached to {@code check}
   */
  @Input
  public abstract Property<Boolean> getAttachToCheck();

  /**
   * The Java toolchain's version, or the reason the Java tools aren't active.
   *
   * @return the toolchain line
   */
  @Input
  public abstract Property<String> getJavaToolchain();

  /**
   * Whether formatting is on.
   *
   * @return whether formatting is on
   */
  @Input
  public abstract Property<Boolean> getFormatEnabled();

  /**
   * The google-java-format style.
   *
   * @return the style
   */
  @Input
  public abstract Property<FormatSettings.Style> getFormatStyle();

  /**
   * Whether Spotless's {@code formatAnnotations()} step is on.
   *
   * @return whether the step is on
   */
  @Input
  public abstract Property<Boolean> getFormatAnnotations();

  /**
   * The git reference formatting ratchets from, if any.
   *
   * @return the ratchet reference
   */
  @Input
  @Optional
  public abstract Property<String> getFormatRatchetFrom();

  /**
   * Whether Error Prone's errors are reported as warnings.
   *
   * @return whether errors are demoted
   */
  @Input
  public abstract Property<Boolean> getErrorsAsWarnings();

  /**
   * Whether Error Prone is on.
   *
   * @return whether Error Prone is on
   */
  @Input
  public abstract Property<Boolean> getErrorProneEnabled();

  /**
   * Whether compiler warnings fail compilation.
   *
   * @return whether warnings are errors
   */
  @Input
  public abstract Property<Boolean> getWarningsAsErrors();

  /**
   * The Error Prone checks the build turned off.
   *
   * @return the disabled check names
   */
  @Input
  public abstract SetProperty<String> getDisabledChecks();

  /**
   * Whether NullAway is on. It's reported off while Error Prone is off.
   *
   * @return whether NullAway is on
   */
  @Input
  public abstract Property<Boolean> getNullAwayEnabled();

  /**
   * Whether NullAway runs in JSpecify mode.
   *
   * @return whether JSpecify mode is on
   */
  @Input
  public abstract Property<Boolean> getJspecifyMode();

  /**
   * Whether SpotBugs is on.
   *
   * @return whether SpotBugs is on
   */
  @Input
  public abstract Property<Boolean> getSpotbugsEnabled();

  /**
   * SpotBugs' analysis effort.
   *
   * @return the effort
   */
  @Input
  public abstract Property<SpotbugsSettings.Effort> getSpotbugsEffort();

  /**
   * The lowest confidence SpotBugs reports.
   *
   * @return the report level
   */
  @Input
  public abstract Property<SpotbugsSettings.Confidence> getSpotbugsReportLevel();

  /**
   * The project's own SpotBugs exclude filter, relative to the project directory, if it set one.
   *
   * @return the filter's path, with {@code /} separators
   */
  @Input
  @Optional
  public abstract Property<String> getExcludeFilter();

  /**
   * SpotBugs' report files by format ({@code xml}, {@code sarif}), relative to the project
   * directory. Empty without the {@code java} plugin.
   *
   * @return the report paths
   */
  @Input
  public abstract MapProperty<String, String> getSpotbugsReports();

  /**
   * JaCoCo's report files by format ({@code xml}), relative to the project directory. Empty without
   * the {@code java} plugin.
   *
   * @return the report paths
   */
  @Input
  public abstract MapProperty<String, String> getCoverageReports();

  /**
   * Whether coverage is on.
   *
   * @return whether coverage is on
   */
  @Input
  public abstract Property<Boolean> getCoverageEnabled();

  /**
   * The minimum instruction coverage, in percent. Out-of-range values fail when read.
   *
   * @return the instruction threshold
   */
  @Input
  public abstract Property<Integer> getInstruction();

  /**
   * The minimum branch coverage, in percent. Out-of-range values fail when read.
   *
   * @return the branch threshold
   */
  @Input
  public abstract Property<Integer> getBranch();

  /**
   * The test tasks whose runs count towards coverage, or none when the {@code java} plugin isn't
   * applied.
   *
   * @return the counted test task names
   */
  @Input
  public abstract ListProperty<String> getCoverageTestTasks();

  /**
   * Whether the Spring Boot main class is left out of coverage: true when the {@code
   * org.springframework.boot} plugin is applied.
   *
   * @return whether the main class is excluded
   */
  @Input
  public abstract Property<Boolean> getExcludesMainClass();

  /**
   * The class-file patterns left out of coverage.
   *
   * @return the exclude patterns
   */
  @Input
  public abstract ListProperty<String> getCoverageExcludes();

  /**
   * Prints the report.
   *
   * <p>It logs at quiet level, so {@code ./gradlew -q stlInfo} still shows it.
   */
  @TaskAction
  public void print() {
    String daemonJvm = JavaVersion.current().getMajorVersion();
    getLogger().quiet(getJson().get() ? renderJson(daemonJvm) : render(daemonJvm));
  }

  /**
   * Builds the report: a version line, then one aligned line per setting.
   *
   * @param daemonJvm the Java version the Gradle daemon runs on, such as {@code 25}
   * @return the report, without a trailing newline
   */
  public String render(String daemonJvm) {
    Map<String, String> versions = getToolVersions().get();
    List<List<String>> rows =
        List.of(
            List.of("Profile", getProfile().get().name()),
            List.of("Gate wired to check", getAttachToCheck().get() ? "yes" : "no"),
            List.of("Daemon JVM", daemonJvm),
            List.of("Java toolchain", getJavaToolchain().get()),
            List.of("Formatting", onOff(getFormatEnabled().get(), formatDetail(versions))),
            List.of("Error Prone", onOff(getErrorProneEnabled().get(), errorProneDetail(versions))),
            List.of("NullAway", nullAwayStatus(versions)),
            List.of("SpotBugs", onOff(getSpotbugsEnabled().get(), spotbugsDetail(versions))),
            List.of("Coverage", onOff(getCoverageEnabled().get(), coverageDetail(versions))));

    StringBuilder report = new StringBuilder("Sterling ").append(versions.get("sterling"));
    for (List<String> row : rows) {
      report.append('\n').append(String.format("%-22s%s", row.get(0) + ":", row.get(1)));
    }
    return report.toString();
  }

  // A tool's version as the project gets it: Sterling's pin, or the project's own choice.
  private String version(Map<String, String> versions, String tool) {
    String override = getVersionOverrides().get().get(tool);
    return override == null ? versions.get(tool) : override + " (project override)";
  }

  private static String onOff(boolean on, String detail) {
    return on ? "on   (" + detail + ")" : "off";
  }

  private String formatDetail(Map<String, String> versions) {
    String style =
        getFormatStyle().get() == FormatSettings.Style.GOOGLE
            ? ""
            : ", " + getFormatStyle().get().name() + " style";
    return "google-java-format "
        + versions.get("google-java-format")
        + style
        + (getFormatAnnotations().get() ? ", formatAnnotations on" : "")
        + (getFormatRatchetFrom().isPresent()
            ? ", ratchet from " + getFormatRatchetFrom().get()
            : "")
        + " via Spotless "
        + versions.get("spotless");
  }

  private String errorProneDetail(Map<String, String> versions) {
    String warnings =
        getWarningsAsErrors().get() ? "warnings are errors" : "warnings are not errors";
    return version(versions, "errorprone")
        + "; "
        + warnings
        + (getErrorsAsWarnings().get() ? "; errors are warnings" : "")
        + count(getDisabledChecks().get().size(), "check", "disabled");
  }

  private String nullAwayStatus(Map<String, String> versions) {
    if (!getNullAwayEnabled().get()) {
      return "off";
    }
    if (!getErrorProneEnabled().get()) {
      return "off (Error Prone off)";
    }
    return "on   ("
        + version(versions, "nullaway")
        + "; JSpecify "
        + version(versions, "jspecify")
        + "; @NullMarked code only"
        + (getJspecifyMode().get() ? "; JSpecify mode" : "")
        + ")";
  }

  private String spotbugsDetail(Map<String, String> versions) {
    String filter = getExcludeFilter().isPresent() ? "; consumer filter merged" : "";
    return version(versions, "spotbugs")
        + "; effort "
        + getSpotbugsEffort().get().name().toLowerCase(Locale.ROOT)
        + "; "
        + getSpotbugsReportLevel().get().name().toLowerCase(Locale.ROOT)
        + " confidence and up"
        + filter;
  }

  private String coverageDetail(Map<String, String> versions) {
    return "JaCoCo "
        + version(versions, "jacoco")
        + "; instruction >= "
        + getInstruction().get()
        + "%, branch >= "
        + getBranch().get()
        + "%"
        + (getCoverageTestTasks().get().isEmpty()
            ? ""
            : "; " + String.join(", ", getCoverageTestTasks().get()))
        + (getExcludesMainClass().get() ? "; Spring Boot main class excluded" : "")
        + count(getCoverageExcludes().get().size(), "exclude", "");
  }

  /**
   * Builds the JSON report: the same configuration as {@link #render(String)}, as one line.
   *
   * @param daemonJvm the Java version the Gradle daemon runs on, such as {@code 25}
   * @return the JSON object, without a trailing newline
   */
  public String renderJson(String daemonJvm) {
    return Json.write(jsonModel(daemonJvm));
  }

  /**
   * The values {@link #renderJson(String)} writes, as nested maps in output order.
   *
   * <p>Keys follow the names of the {@code sterling { }} settings, so a reader can map a key to the
   * setting that controls it.
   *
   * @param daemonJvm the Java version the Gradle daemon runs on, such as {@code 25}
   * @return the report's root object
   */
  public Map<String, Object> jsonModel(String daemonJvm) {
    Map<String, String> versions = getToolVersions().get();
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("schemaVersion", SCHEMA_VERSION);
    root.put("sterling", versions.get("sterling"));
    root.put("project", getProjectPath().get());
    root.put("profile", getProfile().get().name());
    root.put("profileProperty", getProfileProperty().getOrNull());
    root.put("attachToCheck", getAttachToCheck().get());
    root.put("daemonJvm", Integer.parseInt(daemonJvm));
    root.put("javaToolchain", toolchainJson());

    Map<String, Object> format = new LinkedHashMap<>();
    format.put("enabled", getFormatEnabled().get());
    format.put("style", getFormatStyle().get().name());
    format.put("formatAnnotations", getFormatAnnotations().get());
    format.put("ratchetFrom", getFormatRatchetFrom().getOrNull());
    Map<String, Object> formatVersions = new LinkedHashMap<>();
    formatVersions.put("google-java-format", versions.get("google-java-format"));
    formatVersions.put("spotless", versions.get("spotless"));
    format.put("versions", formatVersions);
    root.put("format", format);

    Map<String, Object> errorProne = new LinkedHashMap<>();
    errorProne.put("enabled", getErrorProneEnabled().get());
    putVersion(errorProne, "version", versions, "errorprone");
    errorProne.put("warningsAsErrors", getWarningsAsErrors().get());
    errorProne.put("errorsAsWarnings", getErrorsAsWarnings().get());
    List<String> disabled = new ArrayList<>(getDisabledChecks().get());
    disabled.sort(null);
    errorProne.put("disabledChecks", disabled);
    root.put("errorProne", errorProne);

    Map<String, Object> nullAway = new LinkedHashMap<>();
    nullAway.put("enabled", getNullAwayEnabled().get());
    nullAway.put("active", getNullAwayEnabled().get() && getErrorProneEnabled().get());
    putVersion(nullAway, "version", versions, "nullaway");
    putVersion(nullAway, "jspecify", versions, "jspecify");
    nullAway.put("jspecifyMode", getJspecifyMode().get());
    root.put("nullAway", nullAway);

    Map<String, Object> spotbugs = new LinkedHashMap<>();
    spotbugs.put("enabled", getSpotbugsEnabled().get());
    putVersion(spotbugs, "version", versions, "spotbugs");
    spotbugs.put("effort", getSpotbugsEffort().get().name());
    spotbugs.put("reportLevel", getSpotbugsReportLevel().get().name());
    spotbugs.put("excludeFilter", getExcludeFilter().getOrNull());
    putReports(spotbugs, getSpotbugsReports().get());
    root.put("spotbugs", spotbugs);

    Map<String, Object> coverage = new LinkedHashMap<>();
    coverage.put("enabled", getCoverageEnabled().get());
    putVersion(coverage, "version", versions, "jacoco");
    coverage.put("instruction", getInstruction().get());
    coverage.put("branch", getBranch().get());
    coverage.put("excludes", getCoverageExcludes().get());
    coverage.put("testTasks", getCoverageTestTasks().get());
    coverage.put("excludesMainClass", getExcludesMainClass().get());
    putReports(coverage, getCoverageReports().get());
    root.put("coverage", coverage);
    return root;
  }

  // A version key and its source beside it: "versionSource" for "version", "jspecifySource" for
  // "jspecify". A project's version declared without one, as from a platform, is null.
  private void putVersion(
      Map<String, Object> tool, String key, Map<String, String> versions, String name) {
    String override = getVersionOverrides().get().get(name);
    String source = key.equals("version") ? "versionSource" : key + "Source";
    if (override == null) {
      tool.put(key, versions.get(name));
      tool.put(source, "sterling");
    } else {
      tool.put(key, override.equals(MANAGED) ? null : override);
      tool.put(source, "project");
    }
  }

  // The toolchain line holds the version, or "not active (<reason>)".
  private Map<String, Object> toolchainJson() {
    String line = getJavaToolchain().get();
    Map<String, Object> toolchain = new LinkedHashMap<>();
    if (line.startsWith(NOT_ACTIVE) && line.endsWith(")")) {
      toolchain.put("active", false);
      toolchain.put("reason", line.substring(NOT_ACTIVE.length(), line.length() - 1));
    } else {
      toolchain.put("active", true);
      toolchain.put("version", Integer.parseInt(line));
    }
    return toolchain;
  }

  // Reports exist only once java is applied; the key is left out rather than empty.
  private static void putReports(Map<String, Object> tool, Map<String, String> reports) {
    if (!reports.isEmpty()) {
      tool.put("reports", new TreeMap<>(reports));
    }
  }

  private static String count(int n, String noun, String suffix) {
    if (n == 0) {
      return "";
    }
    String plural = n == 1 ? noun : noun + "s";
    return "; " + n + " " + plural + (suffix.isEmpty() ? "" : " " + suffix);
  }
}
