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

/** Prints Sterling's resolved configuration. Every input is wired from the extension. */
@UntrackedTask(because = "It only prints the resolved configuration.")
public abstract class SterlingInfoTask extends DefaultTask {
  /** Pinned tool versions, keyed by tool, plus {@code sterling} itself. */
  @Input
  public abstract MapProperty<String, String> getToolVersions();

  /** The resolved profile. */
  @Input
  public abstract Property<Profile> getProfile();

  /** Whether {@code check} depends on {@code stlCheck}. */
  @Input
  public abstract Property<Boolean> getAttachToCheck();

  /** The toolchain's Java version, or why Java tools aren't active. */
  @Input
  public abstract Property<String> getJavaToolchain();

  /** Whether formatting is on. */
  @Input
  public abstract Property<Boolean> getFormatEnabled();

  /** Whether Error Prone is on. */
  @Input
  public abstract Property<Boolean> getErrorProneEnabled();

  /** Whether warnings fail compilation. */
  @Input
  public abstract Property<Boolean> getWarningsAsErrors();

  /** Error Prone checks the consumer turned off. */
  @Input
  public abstract SetProperty<String> getDisabledChecks();

  /** Whether NullAway is on. */
  @Input
  public abstract Property<Boolean> getNullAwayEnabled();

  /** Whether SpotBugs is on. */
  @Input
  public abstract Property<Boolean> getSpotbugsEnabled();

  /** Whether the consumer set a SpotBugs exclude filter. */
  @Input
  public abstract Property<Boolean> getConsumerExcludeFilter();

  /** Whether coverage is on. */
  @Input
  public abstract Property<Boolean> getCoverageEnabled();

  /** Minimum instruction coverage, in percent. */
  @Input
  public abstract Property<Integer> getInstruction();

  /** Minimum branch coverage, in percent. */
  @Input
  public abstract Property<Integer> getBranch();

  /** Class-file patterns left out of coverage. */
  @Input
  public abstract ListProperty<String> getCoverageExcludes();

  /** Prints at quiet level, so {@code -q} still shows the one thing this task is for. */
  @TaskAction
  public void print() {
    getLogger().quiet(render(JavaVersion.current().getMajorVersion()));
  }

  /** The report, given the daemon's Java version. */
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
        + "; @NullMarked code only)";
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
