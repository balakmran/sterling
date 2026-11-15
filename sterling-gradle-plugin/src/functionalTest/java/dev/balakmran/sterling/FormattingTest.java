package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spotless with google-java-format, run by stlFmt and stlFmtCheck behind the format flag. */
class FormattingTest {
  private static final String UNFORMATTED =
      """
      import java.util.List;
      public class App {   void f( ) { } ; }
      """;

  private static final String FORMATTED =
      """
      public class App {
        void f() {}
      }
      """;

  @TempDir Path dir;

  private Fixture fixture(String script) {
    return new Fixture(dir)
        .file(
            "build.gradle.kts",
            """
            plugins {
                java
                id("dev.balakmran.sterling")
            }

            repositories { mavenCentral() }

            """
                + script);
  }

  private static boolean ranSpotless(BuildResult result) {
    return result.getTasks().stream()
        .map(BuildTask::getPath)
        .anyMatch(p -> p.startsWith(":spotless"));
  }

  @Test
  void anUnformattedFileFailsStlFmtCheck() {
    BuildResult result =
        fixture("").file("src/main/java/App.java", UNFORMATTED).buildAndFail("stlFmtCheck");

    assertThat(outcome(result, ":spotlessJavaCheck")).isEqualTo(TaskOutcome.FAILED);
    assertThat(outcome(result, ":stlFmtCheck")).isNull();
  }

  @Test
  void stlFmtReformatsDropsRedundantSemicolonsAndUnusedImportsThenTheCheckPasses() {
    Fixture fixture = fixture("").file("src/main/java/App.java", UNFORMATTED);

    BuildResult apply = fixture.build("stlFmt");
    assertThat(outcome(apply, ":spotlessJavaApply")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixture.read("src/main/java/App.java")).isEqualTo(FORMATTED);

    BuildResult check = fixture.build("stlFmtCheck");
    assertThat(outcome(check, ":spotlessJavaCheck"))
        .isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
    assertThat(outcome(check, ":stlFmtCheck")).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
  }

  @Test
  void formatDisabledStlFmtAndStlFmtCheckDontRunSpotless() {
    Fixture fixture =
        fixture("sterling { format { enabled = false } }")
            .file("src/main/java/App.java", UNFORMATTED);

    BuildResult result = fixture.build("stlFmt", "stlFmtCheck");

    assertThat(outcome(result, ":stlFmtCheck")).isEqualTo(TaskOutcome.UP_TO_DATE);
    assertThat(ranSpotless(result)).isFalse();
    assertThat(fixture.read("src/main/java/App.java")).isEqualTo(UNFORMATTED);
  }

  @Test
  void spotlessDoesntAttachToCheckItself() {
    // With the gate detached, anything Spotless ran under check would be its own doing.
    BuildResult result =
        fixture("sterling { attachToCheck = false }")
            .file("src/main/java/App.java", UNFORMATTED)
            .build("check");

    assertThat(ranSpotless(result)).isFalse();
  }

  @Test
  void generatedSourcesUnderBuildGeneratedAreNotFormatChecked() {
    BuildResult result =
        fixture("sourceSets.main { java.srcDir(\"build/generated/sources/openapi\") }\n")
            .file("src/main/java/App.java", FORMATTED)
            .file("build/generated/sources/openapi/Gen.java", "public class Gen {   }\n")
            .build("stlFmtCheck");

    assertThat(outcome(result, ":spotlessJavaCheck")).isEqualTo(TaskOutcome.SUCCESS);
  }

  private static final String FOUR_SPACE =
      """
      public class App {
          void f() {}
      }
      """;

  private static final String AOSP =
      "sterling { format { style = dev.balakmran.sterling.FormatSettings.Style.AOSP } }\n";

  @Test
  void aospStyleAcceptsFourSpaceSources() {
    BuildResult result =
        fixture(AOSP).file("src/main/java/App.java", FOUR_SPACE).build("stlFmtCheck");

    assertThat(outcome(result, ":spotlessJavaCheck")).isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void aospStyleRejectsTwoSpaceSourcesAndStlFmtReindentsThem() {
    Fixture fixture = fixture(AOSP).file("src/main/java/App.java", FORMATTED);

    BuildResult check = fixture.buildAndFail("stlFmtCheck");
    assertThat(outcome(check, ":spotlessJavaCheck")).isEqualTo(TaskOutcome.FAILED);

    BuildResult apply = fixture.build("stlFmt");
    assertThat(outcome(apply, ":spotlessJavaApply")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixture.read("src/main/java/App.java")).isEqualTo(FOUR_SPACE);
  }

  @Test
  void styleIsHonouredWhenSpotlessJavaIsRealisedBeforeTheSterlingBlock() {
    BuildResult result =
        fixture("tasks.named(\"spotlessJava\").get()\n\n" + AOSP)
            .file("src/main/java/App.java", FOUR_SPACE)
            .build("stlFmtCheck");

    assertThat(outcome(result, ":spotlessJavaCheck")).isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void settingStyleAfterSpotlessReadItFailsInsteadOfBeingIgnored() {
    BuildResult result =
        // Realised early, spotlessJava reads the style in Spotless's afterEvaluate, before this
        // one.
        fixture("tasks.named(\"spotlessJava\").get()\nafterEvaluate { " + AOSP.strip() + " }\n")
            .file("src/main/java/App.java", FOUR_SPACE)
            .buildAndFail("stlFmtCheck");

    assertThat(result.getOutput()).contains("style").contains("is final");
    assertThat(outcome(result, ":spotlessJavaCheck")).isNull();
  }

  private static final String RATCHET = "sterling { format { ratchetFrom = \"main\" } }\n";

  // Runs git in the fixture, with an identity and no signing so the user's config doesn't matter.
  private void git(String... args) {
    List<String> command =
        new ArrayList<>(
            List.of(
                "git",
                "-c",
                "user.name=Sterling Test",
                "-c",
                "user.email=test@example.com",
                "-c",
                "commit.gpgsign=false"));
    command.addAll(List.of(args));
    try {
      Process process =
          new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
      String output = new String(process.getInputStream().readAllBytes(), UTF_8);
      if (process.waitFor() != 0) {
        throw new IllegalStateException(String.join(" ", command) + " failed:\n" + output);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  // A repository on main with an unformatted Old.java committed and a formatted New.java.
  private Fixture ratchetFixture(String script) {
    Fixture fixture =
        fixture(script)
            .file(".gitignore", ".gradle/\nbuild/\n")
            .file("src/main/java/Old.java", UNFORMATTED.replace("App", "Old"))
            .file("src/main/java/New.java", FORMATTED.replace("App", "New"));
    git("init", "-q", "-b", "main");
    git("add", ".");
    git("commit", "-q", "-m", "initial");
    return fixture;
  }

  @Test
  void ratchetChecksAndFormatsOnlyFilesChangedSinceTheReference() {
    String newUnformatted = UNFORMATTED.replace("App", "New");
    Fixture fixture = ratchetFixture(RATCHET).file("src/main/java/New.java", newUnformatted);

    BuildResult check = fixture.buildAndFail("stlFmtCheck");
    assertThat(outcome(check, ":spotlessJavaCheck")).isEqualTo(TaskOutcome.FAILED);
    assertThat(check.getOutput()).contains("New.java").doesNotContain("Old.java");

    BuildResult apply = fixture.build("stlFmt");
    assertThat(outcome(apply, ":spotlessJavaApply")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixture.read("src/main/java/New.java")).isEqualTo(FORMATTED.replace("App", "New"));
    assertThat(fixture.read("src/main/java/Old.java")).isEqualTo(UNFORMATTED.replace("App", "Old"));

    BuildResult passing = fixture.build("stlFmtCheck");
    assertThat(outcome(passing, ":spotlessJavaCheck"))
        .isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
  }

  @Test
  void ratchetReusesTheConfigurationCacheAndHonoursAMovedReference() {
    Fixture fixture = ratchetFixture(RATCHET);
    git("checkout", "-q", "-b", "feature");
    fixture.file("src/main/java/Old.java", UNFORMATTED.replace("App", "Older"));
    git("commit", "-q", "-am", "change Old.java");

    BuildResult first = fixture.buildAndFail("stlFmtCheck");
    assertThat(outcome(first, ":spotlessJavaCheck")).isEqualTo(TaskOutcome.FAILED);

    BuildResult second = fixture.buildAndFail("stlFmtCheck");
    assertThat(second.getOutput()).contains("Reusing configuration cache.");
    assertThat(outcome(second, ":spotlessJavaCheck")).isEqualTo(TaskOutcome.FAILED);

    // main now includes the change, so nothing differs from it. Whether Gradle reuses the cache
    // entry or not, the moved ref must be honoured.
    git("branch", "-f", "main", "feature");
    BuildResult third = fixture.build("stlFmtCheck");
    assertThat(outcome(third, ":spotlessJavaCheck"))
        .isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
  }

  @Test
  void ratchetFromAMissingReferenceFailsWithSpotlesssMessage() {
    BuildResult result =
        ratchetFixture("sterling { format { ratchetFrom = \"nope\" } }\n")
            .buildAndFail("stlFmtCheck");

    assertThat(result.getOutput()).contains("No such reference 'nope'");
  }

  @Test
  void anEmptyRatchetFromIsRejected() {
    BuildResult result =
        ratchetFixture("sterling { format { ratchetFrom = \"\" } }\n").buildAndFail("stlFmtCheck");

    assertThat(result.getOutput()).contains("sterling.format.ratchetFrom must not be empty");
    assertThat(outcome(result, ":spotlessJavaCheck")).isNull();
  }
}
