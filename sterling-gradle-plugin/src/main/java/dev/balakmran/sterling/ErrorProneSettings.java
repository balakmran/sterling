package dev.balakmran.sterling;

import java.util.List;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

/** Settings for Error Prone. */
public abstract class ErrorProneSettings {
  /** Whether Error Prone runs during compilation. Default: true. */
  public abstract Property<Boolean> getEnabled();

  /** Whether compiler warnings fail the build ({@code -Werror}). Default from the profile. */
  public abstract Property<Boolean> getWarningsAsErrors();

  /** Error Prone checks turned off, by name. Prefer {@link #disable(String...)}. */
  public abstract SetProperty<String> getDisabledChecks();

  /** Turns off the named Error Prone checks, for example {@code disable("UnusedVariable")}. */
  public void disable(String... checks) {
    getDisabledChecks().addAll(List.of(checks));
  }
}
