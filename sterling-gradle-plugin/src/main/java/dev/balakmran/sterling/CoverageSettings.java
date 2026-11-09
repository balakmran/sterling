package dev.balakmran.sterling;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/** Settings for JaCoCo coverage. */
public abstract class CoverageSettings {
  /** Whether tests record coverage and {@code stlCov} verifies it. Default from the profile. */
  public abstract Property<Boolean> getEnabled();

  /** Minimum instruction coverage, in percent (0 to 100). Default from the profile. */
  public abstract Property<Integer> getInstruction();

  /** Minimum branch coverage, in percent (0 to 100). Default from the profile. */
  public abstract Property<Integer> getBranch();

  /** Ant-style class-file patterns left out of coverage, such as the Spring Boot main class. */
  public abstract ListProperty<String> getExcludes();
}
