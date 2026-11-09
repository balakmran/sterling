package dev.balakmran.sterling;

import java.util.List;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

/**
 * Settings for Error Prone, which finds common bugs while {@code compileJava} runs.
 *
 * <p>Findings at error level fail compilation in every profile; {@link #getWarningsAsErrors()}
 * decides whether warnings do too. For Error Prone options not covered here, configure {@code
 * options.errorprone { }} on the {@code JavaCompile} tasks directly.
 */
public abstract class ErrorProneSettings {
  /** Creates the settings. Gradle instantiates this class for the {@code sterling} extension. */
  public ErrorProneSettings() {}

  /**
   * Whether Error Prone runs during compilation.
   *
   * <p>Defaults to true in every profile. When false, Error Prone isn't downloaded, and NullAway
   * doesn't run either, since it runs inside Error Prone.
   *
   * @return the enabled property
   */
  public abstract Property<Boolean> getEnabled();

  /**
   * Whether compiler warnings fail the build, by passing {@code -Werror} to javac.
   *
   * <p>Defaults to true for {@link Profile#STRICT} and false otherwise. This covers all javac
   * warnings, including javac's own such as {@code [removal]}, not only Error Prone's.
   *
   * @return the warnings-as-errors property
   */
  public abstract Property<Boolean> getWarningsAsErrors();

  /**
   * The Error Prone checks turned off, by check name. Empty by default.
   *
   * <p>{@link #disable(String...)} is the usual way to add to it.
   *
   * @return the disabled checks
   */
  public abstract SetProperty<String> getDisabledChecks();

  /**
   * Turns off Error Prone checks by name, adding to any already turned off.
   *
   * <p>Names are the check names Error Prone prints with each finding, for example {@code
   * disable("UnusedVariable", "MissingSummary")}.
   *
   * @param checks the names of the checks to turn off
   */
  public void disable(String... checks) {
    getDisabledChecks().addAll(List.of(checks));
  }
}
