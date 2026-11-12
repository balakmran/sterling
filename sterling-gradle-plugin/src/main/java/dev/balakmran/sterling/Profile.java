package dev.balakmran.sterling;

import org.gradle.api.InvalidUserDataException;

/**
 * A preset of defaults for every Sterling setting.
 *
 * <p>Pick one in the build script with {@code sterling { profile = Profile.STRICT }}, or without
 * editing it through the {@code sterling.profile} Gradle property, for example {@code
 * -Psterling.profile=strict}. Any setting given explicitly in the {@code sterling} block wins over
 * the profile.
 *
 * <table>
 *   <caption>Defaults by profile</caption>
 *   <tr><th scope="col">Setting</th><th scope="col">LENIENT</th>
 *       <th scope="col">STANDARD</th><th scope="col">STRICT</th></tr>
 *   <tr><th scope="row">{@code attachToCheck}</th><td>false</td><td>true</td><td>true</td></tr>
 *   <tr><th scope="row">{@code format.enabled}</th><td>true</td><td>true</td><td>true</td></tr>
 *   <tr><th scope="row">{@code errorProne.enabled}</th><td>true</td><td>true</td><td>true</td></tr>
 *   <tr><th scope="row">{@code errorProne.warningsAsErrors}</th>
 *       <td>false</td><td>false</td><td>true</td></tr>
 *   <tr><th scope="row">{@code nullAway.enabled}</th><td>true</td><td>true</td><td>true</td></tr>
 *   <tr><th scope="row">{@code nullAway.jspecifyMode}</th>
 *       <td>false</td><td>false</td><td>true</td></tr>
 *   <tr><th scope="row">{@code spotbugs.enabled}</th><td>false</td><td>true</td><td>true</td></tr>
 *   <tr><th scope="row">{@code coverage.enabled}</th><td>false</td><td>true</td><td>true</td></tr>
 *   <tr><th scope="row">{@code coverage.instruction}</th><td>0</td><td>80</td><td>95</td></tr>
 *   <tr><th scope="row">{@code coverage.branch}</th><td>0</td><td>70</td><td>85</td></tr>
 * </table>
 */
public enum Profile {
  /**
   * For adopting Sterling gradually: the tools run on demand, but {@code check} doesn't depend on
   * the gate, and SpotBugs and coverage are off.
   *
   * <p>Error Prone and NullAway still run inside compilation, so their error-level findings still
   * fail the build.
   */
  LENIENT(false, false, false, false, false, 0, 0),

  /**
   * The default: every tool on, {@code check} runs the gate, and coverage must reach 80% of
   * instructions and 70% of branches.
   */
  STANDARD(true, false, false, true, true, 80, 70),

  /**
   * {@link #STANDARD} plus compiler warnings as errors, NullAway's JSpecify mode, and coverage of
   * 95% of instructions and 85% of branches.
   */
  STRICT(true, true, true, true, true, 95, 85);

  /** The Gradle property that chooses the profile when the build script doesn't. */
  static final String PROPERTY = "sterling.profile";

  final boolean attachToCheck;
  final boolean warningsAsErrors;
  final boolean jspecifyMode;
  final boolean spotbugs;
  final boolean coverage;
  final int instruction;
  final int branch;

  Profile(
      boolean attachToCheck,
      boolean warningsAsErrors,
      boolean jspecifyMode,
      boolean spotbugs,
      boolean coverage,
      int instruction,
      int branch) {
    this.attachToCheck = attachToCheck;
    this.warningsAsErrors = warningsAsErrors;
    this.jspecifyMode = jspecifyMode;
    this.spotbugs = spotbugs;
    this.coverage = coverage;
    this.instruction = instruction;
    this.branch = branch;
  }

  /**
   * Reads a profile name as given in the {@code sterling.profile} property.
   *
   * @param value the name, in any case, with surrounding spaces ignored
   * @return the matching profile
   * @throws InvalidUserDataException if the name matches no profile; the message lists the allowed
   *     values
   */
  static Profile parse(String value) {
    for (Profile profile : values()) {
      if (profile.name().equalsIgnoreCase(value.trim())) {
        return profile;
      }
    }
    throw new InvalidUserDataException(
        "Unknown " + PROPERTY + " '" + value + "'. Allowed values: lenient, standard, strict.");
  }
}
