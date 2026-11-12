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

  /**
   * Whether NullAway runs in JSpecify mode, which also checks nullness in generic type arguments,
   * arrays and varargs.
   *
   * <p>Defaults to true only in {@code STRICT}. Spring describes the mode as still under
   * development and recommends turning it on as a second step, once a codebase is clean without it;
   * most false positives on generic and lambda-heavy code come from it.
   *
   * @return the JSpecify mode property
   */
  public abstract Property<Boolean> getJspecifyMode();
}
