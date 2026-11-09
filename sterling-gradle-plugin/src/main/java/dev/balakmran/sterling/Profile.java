package dev.balakmran.sterling;

import org.gradle.api.InvalidUserDataException;

/** Sets the default for every Sterling setting; an explicitly set value always wins. */
public enum Profile {
  /** Tools run on demand; {@code check} doesn't depend on the gate. */
  LENIENT(false, false, false, false, 0, 0),

  /** The default: every tool on, coverage at 80% instructions and 70% branches. */
  STANDARD(true, false, true, true, 80, 70),

  /** Warnings fail compilation; coverage at 95% instructions and 85% branches. */
  STRICT(true, true, true, true, 95, 85);

  static final String PROPERTY = "sterling.profile";

  final boolean attachToCheck;
  final boolean warningsAsErrors;
  final boolean spotbugs;
  final boolean coverage;
  final int instruction;
  final int branch;

  Profile(
      boolean attachToCheck,
      boolean warningsAsErrors,
      boolean spotbugs,
      boolean coverage,
      int instruction,
      int branch) {
    this.attachToCheck = attachToCheck;
    this.warningsAsErrors = warningsAsErrors;
    this.spotbugs = spotbugs;
    this.coverage = coverage;
    this.instruction = instruction;
    this.branch = branch;
  }

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
