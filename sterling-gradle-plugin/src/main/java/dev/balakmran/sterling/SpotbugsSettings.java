package dev.balakmran.sterling;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/** Settings for SpotBugs. */
public abstract class SpotbugsSettings {
  /** Whether {@code stlSec} runs SpotBugs. Default from the profile. */
  public abstract Property<Boolean> getEnabled();

  /** A SpotBugs exclude filter, merged with Sterling's own. */
  public abstract RegularFileProperty getExcludeFilter();
}
