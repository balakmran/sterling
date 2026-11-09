package dev.balakmran.sterling;

import org.gradle.api.provider.Property;

/**
 * Settings for NullAway, which reports possible {@code NullPointerException}s at compile time.
 *
 * <p>NullAway only checks code annotated {@code @NullMarked}, so a project adopts it one package at
 * a time by annotating that package's {@code package-info.java}. It reads JSpecify's annotations;
 * Sterling adds {@code org.jspecify:jspecify} to {@code compileOnly} while NullAway is on.
 */
public abstract class NullAwaySettings {
  /** Creates the settings. Gradle instantiates this class for the {@code sterling} extension. */
  public NullAwaySettings() {}

  /**
   * Whether NullAway checks {@code @NullMarked} code.
   *
   * <p>Defaults to true in every profile, because it does nothing until a package opts in. It needs
   * Error Prone, so it's also off whenever {@link ErrorProneSettings#getEnabled()} is false.
   *
   * @return the enabled property
   */
  public abstract Property<Boolean> getEnabled();
}
