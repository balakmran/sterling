package dev.balakmran.sterling;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * Settings for SpotBugs, which analyses compiled bytecode for bug patterns.
 *
 * <p>{@code stlSec} runs SpotBugs on the main source set and writes HTML, XML and SARIF reports.
 * Sterling's default exclude filter skips patterns that Spring constructor injection and records
 * trigger in ordinary code, and every class compiled from generated sources: source directories
 * under {@code build/generated} and annotation processors' output.
 */
public abstract class SpotbugsSettings {
  /** Creates the settings. Gradle instantiates this class for the {@code sterling} extension. */
  public SpotbugsSettings() {}

  /**
   * Whether {@code stlSec} runs SpotBugs.
   *
   * <p>Defaults to false for {@link Profile#LENIENT} and true otherwise.
   *
   * @return the enabled property
   */
  public abstract Property<Boolean> getEnabled();

  /**
   * A SpotBugs exclude filter of the project's own. Unset by default.
   *
   * <p>Its {@code <Match>} elements are merged with Sterling's default filter, so it adds
   * exclusions rather than replacing Sterling's.
   *
   * @return the exclude filter file
   */
  public abstract RegularFileProperty getExcludeFilter();
}
