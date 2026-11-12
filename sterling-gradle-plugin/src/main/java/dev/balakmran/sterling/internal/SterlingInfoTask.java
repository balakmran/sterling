package dev.balakmran.sterling.internal;

import dev.balakmran.sterling.Profile;
import java.util.List;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * The {@code stlInfo} task: prints Sterling's resolved configuration.
 *
 * <p>Each input is wired from the {@code sterling} extension when the task is registered, so the
 * output shows the values after the profile, the {@code sterling.profile} property and the build
 * script have all been applied. It never stays up to date, since printing is its only effect.
 */
@UntrackedTask(because = "It only prints the resolved configuration.")
public abstract class SterlingInfoTask extends DefaultTask {
  /** Creates the task. Sterling registers it as {@code stlInfo}; Gradle instantiates it. */
  public SterlingInfoTask() {}

  /**
   * The tool versions Sterling pins, keyed by tool name, plus {@code sterling} itself.
   *
   * @return the tool versions
   */
  @Input
  public abstract MapProperty<String, String> getToolVersions();

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
   * Whether the build set a SpotBugs exclude filter of its own.
   *
   * @return whether a consumer filter is set
   */
  @Input
  public abstract Property<Boolean> getConsumerExcludeFilter();

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
    getLogger().quiet(render(JavaVersion.current().getMajorVersion()));
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
            List.of(
                "Formatting",
                onOff(
                    getFormatEnabled().get(),
                    "google-java-format " + versions.get("google-java-format"))),
            List.of("Error Prone", onOff(getErrorProneEnabled().get(), errorProneDetail(versions))),
            List.of("NullAway", nullAwayStatus(versions)),
            List.of("SpotBugs", onOff(getSpotbugsEnabled().get(), spotbugsDetail(versions))),
            List.of("Coverage", onOff(getCoverageEnabled().get(), coverageDetail())));

    StringBuilder report = new StringBuilder("Sterling ").append(versions.get("sterling"));
    for (List<String> row : rows) {
      report.append('\n').append(String.format("%-22s%s", row.get(0) + ":", row.get(1)));
    }
    return report.toString();
  }

  private static String onOff(boolean on, String detail) {
    return on ? "on   (" + detail + ")" : "off";
  }

  private String errorProneDetail(Map<String, String> versions) {
    String warnings =
        getWarningsAsErrors().get() ? "warnings are errors" : "warnings are not errors";
    return versions.get("errorprone")
        + "; "
        + warnings
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
        + versions.get("nullaway")
        + "; JSpecify "
        + versions.get("jspecify")
        + "; @NullMarked code only"
        + (getJspecifyMode().get() ? "; JSpecify mode" : "")
        + ")";
  }

  private String spotbugsDetail(Map<String, String> versions) {
    String filter = getConsumerExcludeFilter().get() ? "; consumer filter merged" : "";
    return versions.get("spotbugs") + filter;
  }

  private String coverageDetail() {
    return "instruction >= "
        + getInstruction().get()
        + "%, branch >= "
        + getBranch().get()
        + "%"
        + count(getCoverageExcludes().get().size(), "exclude", "");
  }

  private static String count(int n, String noun, String suffix) {
    if (n == 0) {
      return "";
    }
    String plural = n == 1 ? noun : noun + "s";
    return "; " + n + " " + plural + (suffix.isEmpty() ? "" : " " + suffix);
  }
}
