import net.ltgt.gradle.errorprone.errorprone

plugins {
  java
  id("org.springframework.boot") version "4.1.2"
  id("io.spring.dependency-management") version "1.1.7"
  id("dev.balakmran.sterling") version "0.1.0"
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
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sterling {
  coverage {
    // 0.1.0's README: tests rarely run the application class.
    excludes.add("com/example/inventory/Application*")
  }
}

tasks.withType<JavaCompile>().configureEach {
  // Copied from Spring's NullAway snippet before Sterling set it.
  options.errorprone.option("NullAway:CustomContractAnnotations", "org.springframework.lang.Contract")
}

tasks.withType<Test> {
  useJUnitPlatform()
}
