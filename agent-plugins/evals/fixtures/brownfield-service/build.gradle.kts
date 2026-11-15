import com.github.spotbugs.snom.Effort
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.11.0"
    id("com.github.spotbugs") version "6.5.13"
    id("net.ltgt.errorprone") version "5.2.0"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.mapstruct:mapstruct:1.6.3")
    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
    testCompileOnly("org.projectlombok:lombok:1.18.48")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.48")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    errorprone("com.google.errorprone:error_prone_core:2.52.0")
    errorprone("com.uber.nullaway:nullaway:0.14.3")
}

// Stands in for an OpenAPI generator: writes the API interface the service implements.
val generatedApi = layout.buildDirectory.dir("generated/openapi/src/main/java")
val generateApi =
    tasks.register("generateApi") {
        outputs.dir(generatedApi)
        doLast {
            val file = generatedApi.get().file("com/example/orders/api/OrdersApi.java").asFile
            file.parentFile.mkdirs()
            file.writeText(
                """
                package com.example.orders.api;

                import com.example.orders.web.OrderDto;
                import java.util.List;

                /** Generated from openapi.yaml. Do not edit. */
                public interface OrdersApi {
                  OrderDto getOrder(String id);

                  List<OrderDto> listOrders();

                  OrderDto placeOrder(OrderDto order);

                  default String apiVersion() {
                    return "1.0.0";
                  }
                }
                """
                    .trimIndent())
        }
    }

sourceSets.main { java.srcDir(generatedApi) }

tasks.compileJava { dependsOn(generateApi) }

spotless {
    // The team never enforced it, so the code is still 4-space and unsorted.
    isEnforceCheck = false
    java {
        target("src/**/*.java")
        googleJavaFormat("1.30.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

spotbugs {
    effort = Effort.MAX
    excludeFilter = file("config/spotbugs-exclude.xml")
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        // Spring's NullAway snippet: nothing but NullAway.
        disableAllChecks = true
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:CustomContractAnnotations", "org.springframework.lang.Contract")
        error("NullAway")
    }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule { limit { minimum = "0.50".toBigDecimal() } }
    }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
