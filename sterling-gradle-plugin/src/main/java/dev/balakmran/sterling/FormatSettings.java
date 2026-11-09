package dev.balakmran.sterling;

import org.gradle.api.provider.Property;

/** Settings for google-java-format. */
public abstract class FormatSettings {
  /** Whether {@code stlFmt} and {@code stlFmtCheck} run. Default: true. */
  public abstract Property<Boolean> getEnabled();
}
