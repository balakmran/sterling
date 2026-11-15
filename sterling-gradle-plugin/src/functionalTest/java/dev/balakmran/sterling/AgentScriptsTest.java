package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The agent plugin's Bash scripts, run against throwaway projects with a real Gradle wrapper. */
class AgentScriptsTest {
  private static final Path SCRIPTS =
      Path.of(
          System.getProperty("sterling.agentPlugins"), "sterling/skills/adopt-sterling/scripts");
  private static final Path WRAPPER = Path.of(System.getProperty("sterling.wrapper"));

  @TempDir Path dir;

  private record Run(int exitCode, String output) {}

  @Test
  void coverageRoundsTheReportTotalsDown() {
    write(
        "report.xml",
        """
        <?xml version="1.0"?><report name="x"><package name="app">\
        <counter type="INSTRUCTION" missed="1" covered="1"/></package>\
        <counter type="INSTRUCTION" missed="33" covered="67"/>\
        <counter type="BRANCH" missed="7" covered="13"/></report>\
        """);

    Run run = run("coverage.sh", "report.xml");

    assertThat(run.exitCode()).isZero();
    assertThat(run.output().trim()).isEqualTo("{\"instruction\":67,\"branch\":65,\"branches\":20}");
  }

  @Test
  void coverageReportsZeroBranchesForCodeWithoutAny() {
    write(
        "report.xml",
        "<report name=\"x\"><counter type=\"INSTRUCTION\" missed=\"0\" covered=\"5\"/></report>");

    assertThat(run("coverage.sh", "report.xml").output().trim())
        .isEqualTo("{\"instruction\":100,\"branch\":0,\"branches\":0}");
  }

  @Test
  void outcomesRejectsAnythingButItsFixedArguments() {
    javaProject();
    for (String arg :
        List.of("-I", "--init-script=x.gradle", "-Dfoo=bar", "clean", ":a:b c", "--offline")) {
      Run run = run("outcomes.sh", arg);
      assertThat(run.exitCode()).describedAs(arg).isEqualTo(2);
      assertThat(run.output()).describedAs(arg).contains("is not allowed");
    }
    assertThat(dir.resolve("build/sterling-agent/outcomes.json")).doesNotExist();
  }

  @Test
  void outcomesRecordsEachTaskWithTheConfigurationCache() {
    javaProject();
    write("src/main/java/app/App.java", "package app;\n\npublic class App {}\n");

    assertThat(run("outcomes.sh", "compileJava").exitCode()).isZero();
    assertThat(read("build/sterling-agent/outcomes.json"))
        .startsWith("{\"exitCode\":0,")
        .contains("{\"path\":\":compileJava\",\"outcome\":\"SUCCESS\"}");

    run("outcomes.sh", "compileJava");
    assertThat(read("build/sterling-agent/outcomes.json"))
        .contains("{\"path\":\":compileJava\",\"outcome\":\"UP_TO_DATE\"}");

    write("src/main/java/app/App.java", "package app;\n\npublic class App { broken }\n");
    run("outcomes.sh", "compileJava", "--continue");
    assertThat(read("build/sterling-agent/outcomes.json"))
        .startsWith("{\"exitCode\":1,")
        .contains("{\"path\":\":compileJava\",\"outcome\":\"FAILED\"}");
  }

  @Test
  void detectStopsOnGradleBelow9WithoutRunningIt() {
    javaProject();
    write(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14-bin.zip\n");

    Run run = run("detect.sh");

    assertThat(run.exitCode()).isEqualTo(3);
    assertThat(run.output())
        .contains("\"supported\":false")
        .contains("Gradle 8.14: Sterling needs Gradle 9.0 or later");
  }

  @Test
  void detectStopsOnADaemonJdkBelow21() {
    javaProject();
    write("gradle/gradle-daemon-jvm.properties", "toolchainVersion=17\n");

    Run run = run("detect.sh");

    assertThat(run.exitCode()).isEqualTo(3);
    assertThat(run.output()).contains("Gradle runs on JDK 17 (gradle-daemon-jvm.properties)");
  }

  @Test
  void detectStopsOnAToolchainBelow21AfterAskingGradle() {
    javaProject();
    write(
        "build.gradle.kts",
        "plugins { java }\n\njava { toolchain { languageVersion = JavaLanguageVersion.of(17) } }\n");

    Run run = run("detect.sh");

    assertThat(run.exitCode()).isEqualTo(3);
    assertThat(run.output()).contains("Java toolchain below 21 in: : (17)");
  }

  @Test
  void detectDescribesToolsDependenciesSourcesAndGit() {
    javaProject();
    write(
        "build.gradle.kts",
        """
        plugins {
            java
            jacoco
            checkstyle
        }

        java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

        sourceSets { main { java { srcDir("build/generated/sources/openapi") } } }

        dependencies {
            compileOnly("org.projectlombok:lombok:1.18.48")
            annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
        }

        tasks.jacocoTestCoverageVerification {
            violationRules { rule { limit { minimum = "0.5".toBigDecimal() } } }
        }
        """);
    write("src/main/java/app/package-info.java", "@NullMarked\npackage app;\n");
    write("src/main/java/app/App.java", "package app;\n\npublic class App {\n    int x;\n}\n");
    write(".pre-commit-config.yaml", "repos: []\n");
    write("build-logic/settings.gradle.kts", "");
    git("init", "-q", "-b", "main");
    git("add", ".");
    git("-c", "user.name=t", "-c", "user.email=t@example.com", "commit", "-q", "-m", "init");

    Run run = run("detect.sh");

    assertThat(run.exitCode()).isZero();
    assertThat(run.output())
        .contains("\"supported\":true")
        .contains("\"git\":{\"repository\":true,\"clean\":true,\"branch\":\"main\"")
        .contains("\"hookManager\":\"pre-commit\"")
        .contains("\"buildLogic\":\"build-logic\"")
        .contains("\"plugins\":[\"java\",\"jacoco\",\"checkstyle\"]")
        .contains("\"lombok\":\"1.18.48\"")
        .contains("\"mapstruct\":\"1.6.3\"")
        .contains("\"toolchain\":21")
        .contains("\"generatedSourceDirs\":[\"build/generated/sources/openapi\"]")
        .contains("\"javaSources\":2,\"nullMarkedPackages\":1,\"fourSpaceIndentShare\":100")
        .contains("\"jacocoRules\":[\"INSTRUCTION:COVEREDRATIO:0.5\"]");
  }

  // A one-module Java build with Sterling's own wrapper, at the Gradle version under test.
  private void javaProject() {
    write("settings.gradle.kts", "rootProject.name = \"consumer\"\n");
    write("build.gradle.kts", "plugins { java }\n");
    // One build per daemon, so the tests leave no daemons behind.
    write("gradle.properties", "org.gradle.daemon=false\norg.gradle.configuration-cache=true\n");
    copy("gradlew");
    copy("gradle/wrapper/gradle-wrapper.jar");
    copy("gradle/wrapper/gradle-wrapper.properties");
    dir.resolve("gradlew").toFile().setExecutable(true);
    String version = Fixture.gradleVersion();
    if (!version.isBlank()) {
      write(
          "gradle/wrapper/gradle-wrapper.properties",
          read("gradle/wrapper/gradle-wrapper.properties")
              .replaceAll("gradle-[0-9.]+-(bin|all)\\.zip", "gradle-" + version + "-bin.zip"));
    }
  }

  private Run run(String script, String... args) {
    List<String> command = new ArrayList<>(List.of("bash", SCRIPTS.resolve(script).toString()));
    command.addAll(List.of(args));
    ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile());
    builder.redirectErrorStream(true);
    builder.environment().put("JAVA_HOME", Fixture.jdk(21));
    try {
      Process process = builder.start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertThat(process.waitFor(10, TimeUnit.MINUTES)).describedAs(script + " finished").isTrue();
      return new Run(process.exitValue(), output);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private void git(String... args) {
    List<String> command = new ArrayList<>(List.of("git"));
    command.addAll(List.of(args));
    try {
      Process process = new ProcessBuilder(command).directory(dir.toFile()).inheritIO().start();
      assertThat(process.waitFor()).describedAs("git " + String.join(" ", args)).isZero();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private void copy(String path) {
    try {
      Path target = dir.resolve(path);
      Files.createDirectories(target.getParent());
      Files.copy(WRAPPER.resolve(path), target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void write(String path, String content) {
    try {
      Path target = dir.resolve(path);
      Files.createDirectories(target.getParent());
      Files.writeString(target, content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String read(String path) {
    try {
      return Files.readString(dir.resolve(path));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
