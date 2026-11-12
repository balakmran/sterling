package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A Spring Boot 4 service with AOT processing: the projects Sterling is built for. */
class SpringBootTest {
  private static final String SPRING_BOOT = "4.1.2";

  @TempDir Path dir;

  @Test
  void aSpringBootServicePassesTheGateAndAotSourcesAreSkipped() {
    Fixture fixture =
        new Fixture(dir)
            .file(
                "build.gradle.kts",
                """
                import org.springframework.boot.gradle.plugin.SpringBootPlugin

                plugins {
                    java
                    id("org.springframework.boot") version "%s"
                    id("org.springframework.boot.aot") version "%s"
                    id("dev.balakmran.sterling")
                }

                // Spring's AOT processing needs a group, as real projects have.
                group = "com.example"

                repositories { mavenCentral() }

                dependencies {
                    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
                    implementation("org.springframework.boot:spring-boot-starter")
                    // processTestAot runs Spring's test AOT processor from here.
                    testImplementation("org.springframework.boot:spring-boot-starter-test")
                }

                testing { suites { named<JvmTestSuite>("test") { useJUnitJupiter() } } }

                sterling { coverage { excludes.add("app/Application*") } }
                """
                    .formatted(SPRING_BOOT, SPRING_BOOT))
            .file(
                "src/main/java/app/Application.java",
                """
                package app;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

                @SpringBootApplication
                @ConfigurationPropertiesScan
                public class Application {
                  public static void main(String[] args) {
                    SpringApplication.run(Application.class, args);
                  }
                }
                """)
            .file(
                "src/main/java/app/GreetingProperties.java",
                """
                package app;

                import org.springframework.boot.context.properties.ConfigurationProperties;

                @ConfigurationProperties("greeting")
                public record GreetingProperties(String prefix) {}
                """)
            .file(
                "src/main/java/app/Greeter.java",
                """
                package app;

                import org.springframework.stereotype.Service;

                @Service
                public class Greeter {
                  private final GreetingProperties properties;

                  public Greeter(GreetingProperties properties) {
                    this.properties = properties;
                  }

                  public String greet(String name) {
                    return properties.prefix() + ", " + name;
                  }
                }
                """)
            .file(
                "src/test/java/app/GreeterTest.java",
                """
                package app;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                import org.junit.jupiter.api.Test;

                class GreeterTest {
                  @Test
                  void greets() {
                    assertEquals("Hello, Ada", new Greeter(new GreetingProperties("Hello")).greet("Ada"));
                  }
                }
                """);
    fixture.build("stlFmt");

    BuildResult result = fixture.build("build", "compileAotJava");

    assertThat(outcome(result, ":stlCheck")).isNotNull();
    List.of(
            ":processAot",
            ":compileAotJava",
            ":spotlessCheck",
            ":compileJava",
            ":spotbugsMain",
            ":jacocoTestCoverageVerification")
        .forEach(task ->
            assertThat(outcome(result, task))
                .describedAs(task)
                .isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE));
    assertThat(result.getOutput()).doesNotContain("aotSources");
  }
}
