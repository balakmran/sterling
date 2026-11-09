package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;

import dev.balakmran.sterling.internal.SpotbugsFilterTask;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultSpotbugsFilterTest {
  @Test
  void theDefaultFilterParsesAndExcludesTheSpringPatterns() {
    String merged = SpotbugsFilterTask.merge(Spotbugs.defaultFilter(), null, List.of());

    assertThat(merged).contains("EI_EXPOSE_REP,EI_EXPOSE_REP2").contains("CT_CONSTRUCTOR_THROW");
  }
}
