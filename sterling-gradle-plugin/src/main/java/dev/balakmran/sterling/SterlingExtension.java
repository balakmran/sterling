package dev.balakmran.sterling;

import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

/**
 * The {@code sterling { }} block: which quality tools run, and how strictly.
 *
 * <p>Every setting takes its default from {@link #getProfile()}. Setting a value explicitly
 * overrides the profile for that value only, so a project can start from a profile and adjust a few
 * settings. For anything Sterling doesn't expose, configure the tool directly through its own DSL,
 * such as {@code spotless { }} or {@code jacoco { }}; that configuration applies alongside
 * Sterling's.
 *
 * <p>In a Kotlin build script, import {@link Profile} to set the profile:
 *
 * <pre>{@code
 * import dev.balakmran.sterling.Profile
 *
 * sterling {
 *     profile = Profile.STRICT
 *     errorProne { disable("UnusedVariable") }
 *     coverage {
 *         instruction = 90
 *         excludes.add("com/example/Application*")
 *     }
 * }
 * }</pre>
 */
public abstract class SterlingExtension {
  /** Creates the extension. Sterling's base plugin registers it as {@code sterling}. */
  public SterlingExtension() {}

  /**
   * The profile that supplies every default.
   *
   * <p>Defaults to the {@code sterling.profile} Gradle property, matched case-insensitively, and to
   * {@link Profile#STANDARD} when the property isn't set. That lets {@code gradle.properties} hold
   * a project's choice and CI override it with {@code -Psterling.profile=strict}. A value set here
   * wins over the property. An unknown property value fails the build, listing the allowed values.
   *
   * @return the profile property
   */
  public abstract Property<Profile> getProfile();

  /**
   * Whether the {@code check} task depends on {@code stlCheck}, making the quality gate part of
   * {@code ./gradlew build}.
   *
   * <p>Defaults to false for {@link Profile#LENIENT} and true otherwise. {@code stlCheck} can
   * always be run directly.
   *
   * @return the attach-to-check property
   */
  public abstract Property<Boolean> getAttachToCheck();

  /**
   * Formatting with google-java-format, run by {@code stlFmt} and checked by {@code stlFmtCheck}.
   *
   * @return the formatting settings
   */
  @Nested
  public abstract FormatSettings getFormat();

  /**
   * Compile-time bug detection with Error Prone, run inside {@code compileJava}.
   *
   * @return the Error Prone settings
   */
  @Nested
  public abstract ErrorProneSettings getErrorProne();

  /**
   * Nullness checking with NullAway, which runs as part of Error Prone.
   *
   * @return the NullAway settings
   */
  @Nested
  public abstract NullAwaySettings getNullAway();

  /**
   * Bytecode analysis with SpotBugs, run by {@code stlSec}.
   *
   * @return the SpotBugs settings
   */
  @Nested
  public abstract SpotbugsSettings getSpotbugs();

  /**
   * Test coverage with JaCoCo, verified by {@code stlCov}.
   *
   * @return the coverage settings
   */
  @Nested
  public abstract CoverageSettings getCoverage();

  /**
   * Configures formatting.
   *
   * @param action applied to {@link #getFormat()}
   */
  public void format(Action<? super FormatSettings> action) {
    action.execute(getFormat());
  }

  /**
   * Configures Error Prone.
   *
   * @param action applied to {@link #getErrorProne()}
   */
  public void errorProne(Action<? super ErrorProneSettings> action) {
    action.execute(getErrorProne());
  }

  /**
   * Configures NullAway.
   *
   * @param action applied to {@link #getNullAway()}
   */
  public void nullAway(Action<? super NullAwaySettings> action) {
    action.execute(getNullAway());
  }

  /**
   * Configures SpotBugs.
   *
   * @param action applied to {@link #getSpotbugs()}
   */
  public void spotbugs(Action<? super SpotbugsSettings> action) {
    action.execute(getSpotbugs());
  }

  /**
   * Configures coverage.
   *
   * @param action applied to {@link #getCoverage()}
   */
  public void coverage(Action<? super CoverageSettings> action) {
    action.execute(getCoverage());
  }
}
