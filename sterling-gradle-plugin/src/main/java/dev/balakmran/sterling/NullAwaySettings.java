package dev.balakmran.sterling;

import org.gradle.api.provider.Property;

/** Settings for NullAway. */
public abstract class NullAwaySettings {
  /** Whether NullAway checks {@code @NullMarked} code. Needs Error Prone. Default: true. */
  public abstract Property<Boolean> getEnabled();
}
