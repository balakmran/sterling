package dev.balakmran.sterling;

import org.gradle.api.provider.Property;

/**
 * Settings for formatting Java sources with google-java-format, through Spotless.
 *
 * <p>{@code stlFmt} rewrites sources in place and {@code stlFmtCheck} fails on any file that isn't
 * formatted. Sources under {@code build/generated} are left alone. Spotless's own tasks, such as
 * {@code spotlessApply}, keep working whatever these settings say.
 */
public abstract class FormatSettings {
  /** Creates the settings. Gradle instantiates this class for the {@code sterling} extension. */
  public FormatSettings() {}

  /**
   * Whether {@code stlFmt} and {@code stlFmtCheck} run Spotless.
   *
   * <p>Defaults to true in every profile. When false, both tasks do nothing and google-java-format
   * isn't downloaded.
   *
   * @return the enabled property
   */
  public abstract Property<Boolean> getEnabled();

  /**
   * The google-java-format style.
   *
   * <p>Defaults to {@link Style#GOOGLE} in every profile. {@link Style#AOSP} runs the same pinned
   * formatter with 4-space indentation, which reformats far less of a codebase that already indents
   * by 4.
   *
   * <p>Spotless reads it once, in its own {@code afterEvaluate} or when {@code spotlessJava} is
   * realised, whichever is later, so the {@code sterling { }} block is always seen. The value is
   * final from then on: a build that realises {@code spotlessJava} early and sets it after
   * Spotless's {@code afterEvaluate} fails instead of having the value ignored.
   *
   * @return the style property
   */
  public abstract Property<Style> getStyle();

  /**
   * A git reference to format against, such as {@code origin/main}: only files that differ from it
   * are checked and formatted.
   *
   * <p>Unset by default, which formats every file. Spotless compares each file with the merge base
   * of {@code HEAD} and this reference, so the reference must exist locally: a shallow CI checkout
   * needs the full history, or a {@code git fetch} of that branch, first. An empty string is
   * rejected; leave the setting unset to turn ratchet off.
   *
   * <p>It applies to Sterling's Java format only; any other Spotless format keeps its own setting.
   * Like {@link #getStyle()}, it's read when Spotless configures {@code spotlessJava} and is final
   * from then on.
   *
   * @return the ratchet reference property
   */
  public abstract Property<String> getRatchetFrom();

  /** A google-java-format style. */
  public enum Style {
    /** Google Java Style: 2-space indentation, 4 for continuation lines. */
    GOOGLE,
    /** Android Open Source Project style: 4-space indentation, 8 for continuation lines. */
    AOSP
  }
}
