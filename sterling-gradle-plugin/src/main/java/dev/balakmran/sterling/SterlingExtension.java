package dev.balakmran.sterling;

import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

/** The {@code sterling { }} block. Unset values default from {@link #getProfile()}. */
public abstract class SterlingExtension {
  /** Defaults for every setting: the {@code sterling.profile} property, else STANDARD. */
  public abstract Property<Profile> getProfile();

  /** Whether {@code check} depends on {@code stlCheck}. Default from the profile. */
  public abstract Property<Boolean> getAttachToCheck();

  /** Formatting with google-java-format. */
  @Nested
  public abstract FormatSettings getFormat();

  /** Compile-time analysis with Error Prone. */
  @Nested
  public abstract ErrorProneSettings getErrorProne();

  /** Nullness checking with NullAway, inside Error Prone. */
  @Nested
  public abstract NullAwaySettings getNullAway();

  /** Bytecode analysis with SpotBugs. */
  @Nested
  public abstract SpotbugsSettings getSpotbugs();

  /** Coverage verification with JaCoCo. */
  @Nested
  public abstract CoverageSettings getCoverage();

  /** Configures {@link #getFormat()}. */
  public void format(Action<? super FormatSettings> action) {
    action.execute(getFormat());
  }

  /** Configures {@link #getErrorProne()}. */
  public void errorProne(Action<? super ErrorProneSettings> action) {
    action.execute(getErrorProne());
  }

  /** Configures {@link #getNullAway()}. */
  public void nullAway(Action<? super NullAwaySettings> action) {
    action.execute(getNullAway());
  }

  /** Configures {@link #getSpotbugs()}. */
  public void spotbugs(Action<? super SpotbugsSettings> action) {
    action.execute(getSpotbugs());
  }

  /** Configures {@link #getCoverage()}. */
  public void coverage(Action<? super CoverageSettings> action) {
    action.execute(getCoverage());
  }
}
