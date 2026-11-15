package dev.balakmran.sterling;

import static dev.balakmran.sterling.Fixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Lombok: its code compiles cleanly under Error Prone and NullAway, and stays out of coverage
 * because Lombok marks it {@code @lombok.Generated}.
 */
class LombokTest {
  private static final String LOMBOK = "1.18.48";

  @TempDir Path dir;

  private Fixture lombokProject() {
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
                compileOnly("org.projectlombok:lombok:%s")
                annotationProcessor("org.projectlombok:lombok:%s")
            }

            testing { suites { named<JvmTestSuite>("test") { useJUnitJupiter() } } }
            """
                .formatted(LOMBOK, LOMBOK))
        .file(
            "src/main/java/app/package-info.java",
            """
            @NullMarked
            package app;

            import org.jspecify.annotations.NullMarked;
            """)
        .file(
            "src/main/java/app/Order.java",
            """
            package app;

            import lombok.Builder;
            import lombok.Value;
            import org.jspecify.annotations.Nullable;

            @Value
            @Builder
            public class Order {
              String id;
              int quantity;
              @Builder.Default @Nullable String note = null;

              public String describe() {
                return id + " x" + quantity;
              }
            }
            """)
        .file(
            "src/main/java/app/Customer.java",
            """
            package app;

            import lombok.Data;
            import org.jspecify.annotations.Nullable;

            @Data
            public class Customer {
              private String name = "";
              private @Nullable String email;
            }
            """)
        .file(
            "src/test/java/app/OrderTest.java",
            """
            package app;

            import static org.junit.jupiter.api.Assertions.assertEquals;

            import org.junit.jupiter.api.Test;

            class OrderTest {
              @Test
              void describesTheOrder() {
                Order order = Order.builder().id("a").quantity(2).build();
                Customer customer = new Customer();
                customer.setName("b");

                assertEquals("a x2", order.describe());
                assertEquals("b", customer.getName());
              }
            }
            """);
  }

  @Test
  void lombokCodePassesTheGateAndStaysOutOfCoverage() {
    BuildResult result = lombokProject().build("stlCheck");

    assertThat(outcome(result, ":compileJava")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":spotbugsMain")).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(outcome(result, ":jacocoTestCoverageVerification")).isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void withoutTheGeneratedAnnotationLombokCodeCountsAgainstCoverage() {
    BuildResult result =
        lombokProject()
            .file("lombok.config", "lombok.addLombokGeneratedAnnotation = false\n")
            .buildAndFail("stlCov");

    assertThat(outcome(result, ":jacocoTestCoverageVerification")).isEqualTo(TaskOutcome.FAILED);
  }
}
