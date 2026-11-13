package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.spotbugs.snom.Confidence;
import com.github.spotbugs.snom.Effort;
import org.junit.jupiter.api.Test;

class SpotbugsLevelsTest {
  @Test
  void everyEffortMapsToTheSpotbugsLevelOfTheSameName() {
    for (SpotbugsSettings.Effort effort : SpotbugsSettings.Effort.values()) {
      assertThat(Spotbugs.effort(effort).name()).isEqualTo(effort.name());
    }
    assertThat(Spotbugs.effort(SpotbugsSettings.Effort.MAX)).isEqualTo(Effort.MAX);
  }

  @Test
  void everyReportLevelMapsToTheSpotbugsConfidenceOfTheSameName() {
    for (SpotbugsSettings.Confidence confidence : SpotbugsSettings.Confidence.values()) {
      assertThat(Spotbugs.confidence(confidence).name()).isEqualTo(confidence.name());
    }
    assertThat(Spotbugs.confidence(SpotbugsSettings.Confidence.LOW)).isEqualTo(Confidence.LOW);
  }
}
