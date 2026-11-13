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
   * How hard SpotBugs analyses: more effort finds more bugs and takes longer and more memory.
   *
   * <p>Defaults to {@link Effort#MAX} for {@link Profile#STRICT} and {@link Effort#DEFAULT}
   * otherwise. In a Kotlin build script, import {@link SpotbugsSettings} and write {@code effort =
   * SpotbugsSettings.Effort.MAX}.
   *
   * @return the effort property
   */
  public abstract Property<Effort> getEffort();

  /**
   * The lowest confidence a finding needs to be reported, and so to fail {@code stlSec}.
   *
   * <p>Defaults to {@link Confidence#HIGH} for {@link Profile#LENIENT} and {@link
   * Confidence#MEDIUM} otherwise. {@link Confidence#LOW} adds findings SpotBugs is unsure of, such
   * as a method declared {@code throws Exception}, which Spring's own APIs often require.
   *
   * @return the report level property
   */
  public abstract Property<Confidence> getReportLevel();

  /**
   * A SpotBugs analysis effort, the same levels as SpotBugs' {@code -effort} option.
   *
   * <p>{@link #MORE} is the same as {@link #DEFAULT} on current SpotBugs releases.
   */
  public enum Effort {
    /** The least analysis: fastest, and finds the fewest bugs. */
    MIN,
    /** Less analysis than the default. */
    LESS,
    /** SpotBugs' own default. */
    DEFAULT,
    /** More analysis than the default. */
    MORE,
    /** The most analysis, including more interprocedural analysis: slowest, and finds the most. */
    MAX
  }

  /** How sure SpotBugs must be of a finding to report it. */
  public enum Confidence {
    /** Reports every finding, including those SpotBugs is unsure of. */
    LOW,
    /** Reports findings of medium and high confidence: SpotBugs' own default. */
    MEDIUM,
    /** Reports only the findings SpotBugs is most sure of. */
    HIGH
  }

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
