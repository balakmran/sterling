package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.gradle.api.InvalidUserDataException;
import org.junit.jupiter.api.Test;

class ProfileTest {
  @Test
  void parsesNamesCaseInsensitively() {
    assertThat(Profile.parse("strict")).isEqualTo(Profile.STRICT);
    assertThat(Profile.parse(" Lenient ")).isEqualTo(Profile.LENIENT);
    assertThat(Profile.parse("STANDARD")).isEqualTo(Profile.STANDARD);
  }

  @Test
  void rejectsUnknownNamesListingTheAllowedValues() {
    assertThatThrownBy(() -> Profile.parse("bogus"))
        .isInstanceOf(InvalidUserDataException.class)
        .hasMessage("Unknown sterling.profile 'bogus'. Allowed values: lenient, standard, strict.");
  }
}
