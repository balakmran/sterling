package dev.balakmran.sterling;

import java.util.List;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

/**
 * Settings for Error Prone, which finds common bugs while {@code compileJava} runs.
 *
 * <p>Findings at error level fail compilation unless {@link #getErrorsAsWarnings()} is on, as it is
 * in {@code LENIENT}; {@link #getWarningsAsErrors()} decides whether warnings fail it too. For
 * Error Prone options not covered here, configure {@code options.errorprone { }} on the {@code
 * JavaCompile} tasks directly.
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
   * warnings, including javac's own such as {@code [removal]}, not only Error Prone's, and applies
   * even while Error Prone is off.
   *
   * @return the warnings-as-errors property
   */
  public abstract Property<Boolean> getWarningsAsErrors();

  /**
   * Whether Error Prone's error-level findings, NullAway's included, are reported as warnings
   * instead of failing compilation, by passing {@code -XepAllErrorsAsWarnings}.
   *
   * <p>Defaults to true for {@link Profile#LENIENT} and false otherwise, so adding Sterling to an
   * existing codebase doesn't break compilation. A package marked {@code @NullMarked} while on
   * {@code LENIENT} shows its NullAway findings as warnings, and {@link Profile#STANDARD} turns
   * them back into errors. While it's on, Sterling also raises javac's cap of 100 printed warnings,
   * so the findings aren't hidden.
   *
   * <p>Setting it together with {@link #getWarningsAsErrors()} fails compilation with a message
   * naming both: {@code -Werror} would turn the demoted errors back into failures. A project's own
   * {@code options.errorprone.allErrorsAsWarnings} wins.
   *
   * @return the errors-as-warnings property
   */
  public abstract Property<Boolean> getErrorsAsWarnings();

  /**
   * The Error Prone checks {@code stlFix} patches, by check name.
   *
   * <p>Defaults to {@code AnnotationPosition}, which moves type-use annotations such as JSpecify's
   * {@code @Nullable} to where the Google Java Style Guide puts them (§4.8.5.1), and a short list
   * of checks whose fixes keep a program's behaviour: {@code MissingOverride}, {@code
   * UnnecessaryParentheses}, {@code OperatorPrecedence}, {@code PatternMatchingInstanceof} and
   * {@code EmptyBlockTag}. {@link #disable(String...)} removes a check from the run too. Not every
   * suggested fix keeps behaviour (removing an unused variable can drop a side effect), so add
   * checks only after reading what they rewrite.
   *
   * <p>{@code stlFix} enables each of these checks, as a warning, for its own run only; it doesn't
   * turn them on for ordinary builds. {@code add} appends to the defaults.
   *
   * @return the patched checks
   */
  public abstract SetProperty<String> getPatchChecks();

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
