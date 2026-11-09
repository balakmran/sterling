package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SpotBugs on the main classes, run by stlSec behind the spotbugs flag. */
class SpotbugsTest {
  private static final String SPOTBUGS = ":spotbugsMain";

  // Dereferences a value that is always null: a high-priority correctness bug.
  private static final String BAD =
      """
      package app;

      public class Bad {
        public int length() {
          String text = null;
          return text.length();
        }
      }
      """;

  // Returns a mutable field: EI_EXPOSE_REP, which Sterling's default filter excludes.
  private static final String HOLDER =
      """
      package app;

      import java.util.Date;

      public class Holder {
        private final Date created = new Date();

        public Date created() {
          return created;
        }
      }
      """;

  @TempDir Path dir;

  private Fixture fixture(String sterling) {
    return new Fixture(dir)
        .file(
            "build.gradle.kts",
            """
            plugins {
                java
                id("dev.balakmran.sterling.java")
            }

            repositories { mavenCentral() }

            """
                + sterling);
  }

  @Test
  void aBugFailsStlSecAndWritesHtmlXmlAndSarifReports() {
    BuildResult result = fixture("").file("src/main/java/app/Bad.java", BAD).buildAndFail("stlSec");

    assertThat(outcome(result, SPOTBUGS)).isEqualTo(TaskOutcome.FAILED);
    assertThat(dir.resolve("build/reports/spotbugs"))
        .isDirectoryContaining("glob:**.html")
        .isDirectoryContaining("glob:**.xml")
        .isDirectoryContaining("glob:**.sarif");
  }

  @Test
  void spotbugsDisabledStlSecDoesntRunIt() {
    BuildResult result =
        fixture("sterling { spotbugs { enabled = false } }")
            .file("src/main/java/app/Bad.java", BAD)
            .build("stlSec");

    assertThat(outcome(result, ":stlSec")).isEqualTo(TaskOutcome.UP_TO_DATE);
    assertThat(outcome(result, SPOTBUGS)).isNull();
  }

  @Test
  void theProjectsFilterAndSterlingsDefaultFilterBothApply() {
    BuildResult result =
        fixture("sterling { spotbugs { excludeFilter = file(\"config/spotbugs-exclude.xml\") } }")
            .file(
                "config/spotbugs-exclude.xml",
                """
                <FindBugsFilter>
                  <Match>
                    <Class name="app.Bad"/>
                  </Match>
                </FindBugsFilter>
                """)
            .file("src/main/java/app/Bad.java", BAD)
            .file("src/main/java/app/Holder.java", HOLDER)
            .build("stlSec");

    assertThat(outcome(result, SPOTBUGS)).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":stlSpotbugsFilter")).isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void spotbugsDoesntAttachToCheckItself() {
    // With the gate detached, a SpotBugs run under check would be its own doing.
    BuildResult result =
        fixture("sterling { attachToCheck = false }")
            .file("src/main/java/app/Bad.java", BAD)
            .build("check");

    assertThat(outcome(result, SPOTBUGS)).isNull();
  }

  @Test
  void usesThePinnedSpotbugsEngine() {
    BuildResult result =
        fixture("")
            .build("dependencyInsight", "--configuration", "spotbugs", "--dependency", "spotbugs");

    assertThat(result.getOutput())
        .contains("com.github.spotbugs:spotbugs:" + System.getProperty("sterling.spotbugsVersion"));
  }
}
