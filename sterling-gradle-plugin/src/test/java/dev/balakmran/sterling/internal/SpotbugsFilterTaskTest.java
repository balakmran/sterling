package dev.balakmran.sterling.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

class SpotbugsFilterTaskTest {
  private static final String STERLING =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <!-- a comment outside any Match -->
      <FindBugsFilter>
        <Match>
          <Bug pattern="EI_EXPOSE_REP"/>
        </Match>
      </FindBugsFilter>
      """;

  @Test
  void keepsSterlingsMatchesFirstThenTheProjects() {
    String consumer =
        """
        <FindBugsFilter xmlns="https://github.com/spotbugs/filter/3.0.0">
          <Match>
            <Class name="app.Bad"/>
          </Match>
        </FindBugsFilter>
        """;

    String merged = SpotbugsFilterTask.merge(STERLING, consumer, List.of());

    assertThat(merged)
        .contains("<FindBugsFilter>")
        .doesNotContain("xmlns")
        .doesNotContain("a comment");
    assertThat(merged.indexOf("EI_EXPOSE_REP")).isLessThan(merged.indexOf("app.Bad"));
  }

  @Test
  void withoutAProjectFilterKeepsSterlingsOnly() {
    String merged = SpotbugsFilterTask.merge(STERLING, null, List.of());

    assertThat(merged).contains("EI_EXPOSE_REP");
    assertThat(merged.split("<Match>", -1)).hasSize(2);
  }

  @Test
  void excludesEachGeneratedClassAndItsNestedClasses() {
    String merged = SpotbugsFilterTask.merge(STERLING, null, List.of("gen.api.OrdersApi"));

    assertThat(merged).contains("<Class name=\"~gen\\.api\\.OrdersApi(\\$.*)?\"/>");
    assertThat("gen.api.OrdersApi").matches("gen\\.api\\.OrdersApi(\\$.*)?");
    assertThat("gen.api.OrdersApi$Builder").matches("gen\\.api\\.OrdersApi(\\$.*)?");
    assertThat("gen.api.OrdersApiImpl").doesNotMatch("gen\\.api\\.OrdersApi(\\$.*)?");
  }

  @Test
  void rejectsDocumentTypeDeclarations() {
    String withEntity =
        """
        <?xml version="1.0"?>
        <!DOCTYPE FindBugsFilter [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
        <FindBugsFilter><Match><Class name="&secret;"/></Match></FindBugsFilter>
        """;

    assertThatThrownBy(() -> SpotbugsFilterTask.merge(STERLING, withEntity, List.of()))
        .isInstanceOf(GradleException.class)
        .hasMessageStartingWith("sterling.spotbugs.excludeFilter isn't valid XML");
  }

  @Test
  void rejectsAnotherRootElement() {
    assertThatThrownBy(() -> SpotbugsFilterTask.merge(STERLING, "<Filters/>", List.of()))
        .isInstanceOf(GradleException.class)
        .hasMessage(
            "sterling.spotbugs.excludeFilter must have a <FindBugsFilter> root element, not"
                + " <Filters>.");
  }
}
