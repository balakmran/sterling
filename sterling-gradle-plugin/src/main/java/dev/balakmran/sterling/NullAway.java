package dev.balakmran.sterling;

import java.util.Map;
import net.ltgt.gradle.errorprone.CheckSeverity;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

/**
 * NullAway's part of the Error Prone setup: its jar, JSpecify's annotations, and its options.
 *
 * <p>NullAway runs as an Error Prone check, so it's only active while both {@code
 * errorProne.enabled} and {@code nullAway.enabled} are on. {@link ErrorProne} applies the check
 * settings in the right order; this class holds what they contain.
 */
final class NullAway {
  static final String CHECK = "NullAway";

  private NullAway() {}

  /**
   * Whether NullAway runs: it needs Error Prone.
   *
   * @param sterling the project's {@code sterling} extension
   * @return true while both Error Prone and NullAway are on
   */
  static Provider<Boolean> active(SterlingExtension sterling) {
    return sterling
        .getErrorProne()
        .getEnabled()
        .zip(sterling.getNullAway().getEnabled(), (errorProne, nullAway) -> errorProne && nullAway);
  }

  /**
   * Adds NullAway to the {@code errorprone} configuration, and JSpecify to {@code compileOnly} and
   * {@code testCompileOnly}, while NullAway is active.
   *
   * <p>JSpecify is compile-only: annotations aren't needed at runtime, and Spring Boot apps already
   * get it through {@code spring-core}.
   *
   * @param project a project with the {@code java} and {@code net.ltgt.errorprone} plugins applied
   * @param active whether NullAway runs
   */
  static void addDependencies(Project project, Provider<Boolean> active) {
    ToolDependencies.addWhenOn(
        project,
        "errorprone",
        "com.uber.nullaway",
        "nullaway",
        ToolVersions.get("nullaway"),
        active);
    for (String configuration : new String[] {"compileOnly", "testCompileOnly"}) {
      ToolDependencies.addWhenOn(
          project, configuration, "org.jspecify", "jspecify", ToolVersions.get("jspecify"), active);
    }
  }

  /**
   * NullAway's check severity: error while active, nothing while not, since naming the check
   * without its jar would fail Error Prone.
   *
   * @param active whether NullAway runs
   * @return the check entries to add to Error Prone's options
   */
  static Provider<Map<String, CheckSeverity>> checks(Provider<Boolean> active) {
    return active.map(on -> on ? Map.of(CHECK, CheckSeverity.ERROR) : Map.of());
  }

  /**
   * NullAway's options while active: check only {@code @NullMarked} code, and use JSpecify mode
   * when {@code jspecifyMode} is on.
   *
   * @param active whether NullAway runs
   * @param jspecifyMode whether NullAway runs in JSpecify mode
   * @return the check options to add to Error Prone's options
   */
  static Provider<Map<String, String>> checkOptions(
      Provider<Boolean> active, Provider<Boolean> jspecifyMode) {
    return active.zip(jspecifyMode, NullAway::options);
  }

  /**
   * The option entries {@link #checkOptions} provides.
   *
   * @param active whether NullAway runs
   * @param jspecifyMode whether NullAway runs in JSpecify mode
   * @return no options while NullAway is off; otherwise {@code OnlyNullMarked}, plus {@code
   *     JSpecifyMode} when it's on
   */
  static Map<String, String> options(boolean active, boolean jspecifyMode) {
    if (!active) {
      return Map.of();
    }
    return jspecifyMode
        ? Map.of(CHECK + ":OnlyNullMarked", "true", CHECK + ":JSpecifyMode", "true")
        : Map.of(CHECK + ":OnlyNullMarked", "true");
  }
}
