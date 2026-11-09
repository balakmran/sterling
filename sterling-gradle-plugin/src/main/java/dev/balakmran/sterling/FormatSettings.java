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
}
