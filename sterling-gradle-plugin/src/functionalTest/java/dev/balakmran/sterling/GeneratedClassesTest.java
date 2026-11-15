package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Classes compiled from generated sources: an OpenAPI-style source directory under {@code
 * build/generated} and a MapStruct mapper's implementation, listed once and left out by SpotBugs.
 */
class GeneratedClassesTest {
  private static final String MAPSTRUCT = "1.6.3";

  @TempDir Path dir;

  private Fixture fixture(String extra) {
    return new Fixture(dir)
        .file(
            "build.gradle.kts",
            """
            plugins {
                java
                id("dev.balakmran.sterling")
            }

            repositories { mavenCentral() }

            dependencies {
                implementation("org.mapstruct:mapstruct:%s")
                annotationProcessor("org.mapstruct:mapstruct-processor:%s")
            }

            sourceSets { main { java.srcDir("build/generated/sources/openapi/java/main") } }

            testing { suites { named<JvmTestSuite>("test") { useJUnitJupiter() } } }

            """
                    .formatted(MAPSTRUCT, MAPSTRUCT)
                + extra)
        .file(
            "build/generated/sources/openapi/java/main/gen/api/OrderDto.java",
            """
            package gen.api;

            public class OrderDto {
              private String id;

              public String getId() {
                return id;
              }

              public void setId(String id) {
                this.id = id;
              }

              public static class Builder {
                public OrderDto build() {
                  return new OrderDto();
                }
              }
            }
            """)
        .file("src/main/java/app/Order.java", "package app;\n\npublic record Order(String id) {}\n")
        .file(
            "src/main/java/app/OrderView.java",
            "package app;\n\npublic record OrderView(String id) {}\n")
        .file(
            "src/main/java/app/OrderMapper.java",
            """
            package app;

            import org.mapstruct.Mapper;

            @Mapper
            public interface OrderMapper {
              OrderView toView(Order order);
            }
            """);
  }

  @Test
  void listsGeneratedClassesAndSpotbugsSkipsThem() {
    Fixture fixture = fixture("");

    BuildResult result = fixture.build("stlSpotbugsFilter");

    assertThat(outcome(result, ":stlGeneratedClasses")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":compileJava")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixture.read("build/sterling/generated-classes.txt"))
        .isEqualTo("app.OrderMapperImpl\ngen.api.OrderDto\n");
    // The same <Match> elements stlSpotbugsFilter wrote when it scanned the directories itself.
    assertThat(fixture.read("build/sterling/spotbugs-exclude.xml"))
        .contains(
            """
              <Match>
                <Class name="~app\\.OrderMapperImpl(\\$.*)?"/>
              </Match>
              <Match>
                <Class name="~gen\\.api\\.OrderDto(\\$.*)?"/>
              </Match>
            </FindBugsFilter>\
            """);
  }

  // Covers every hand-written class; the generated ones stay untested.
  private static final String TESTS =
      """
      package app;

      import static org.junit.jupiter.api.Assertions.assertEquals;

      import org.junit.jupiter.api.Test;

      class OrderTest {
        @Test
        void records() {
          assertEquals("1", new Order("1").id());
          assertEquals("1", new OrderView("1").id());
        }
      }
      """;

  @Test
  void coverageLeavesOutGeneratedClasses() {
    Fixture fixture = fixture("").file("src/test/java/app/OrderTest.java", TESTS);

    BuildResult result = fixture.build("stlCov", "jacocoTestReport");

    assertThat(outcome(result, ":jacocoTestCoverageVerification"))
        .describedAs(result.getOutput())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixture.read("build/sterling/generated-classes.txt"))
        .isEqualTo("app.OrderMapperImpl\ngen.api.OrderDto\n");
    assertThat(fixture.read("build/reports/jacoco/test/jacocoTestReport.xml"))
        .contains("app/Order\"")
        .doesNotContain("OrderMapperImpl")
        .doesNotContain("OrderDto");
  }

  @Test
  void coverageReusesTheConfigurationCacheAndStaysUpToDate() {
    Fixture fixture = fixture("").file("src/test/java/app/OrderTest.java", TESTS);
    fixture.build("stlCov");

    BuildResult second = fixture.build("stlCov");

    assertThat(second.getOutput()).contains("Reusing configuration cache.");
    assertThat(outcome(second, ":jacocoTestCoverageVerification"))
        .isEqualTo(TaskOutcome.UP_TO_DATE);
  }

  @Test
  void theListIsUpToDateUntilTheGeneratedSourcesChange() {
    Fixture fixture = fixture("");
    fixture.build("stlGeneratedClasses");

    assertThat(outcome(fixture.build("stlGeneratedClasses"), ":stlGeneratedClasses"))
        .isEqualTo(TaskOutcome.UP_TO_DATE);

    fixture.file(
        "build/generated/sources/openapi/java/main/gen/api/CustomerDto.java",
        "package gen.api;\n\npublic class CustomerDto {}\n");
    BuildResult changed = fixture.build("stlGeneratedClasses");

    assertThat(outcome(changed, ":stlGeneratedClasses")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(fixture.read("build/sterling/generated-classes.txt"))
        .contains("gen.api.CustomerDto");
  }
}
