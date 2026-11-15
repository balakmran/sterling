import com.github.spotbugs.snom.Effort

plugins {
    java
    jacoco
    id("com.diffplug.spotless")
    id("com.github.spotbugs")
}

repositories { mavenCentral() }

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

val libs = versionCatalogs.named("libs")

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.findLibrary("assertj-core").get())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

spotless {
    isEnforceCheck = false
    java {
        googleJavaFormat("1.30.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

spotbugs { effort = Effort.MAX }

tasks.jacocoTestReport { reports { xml.required = true } }
