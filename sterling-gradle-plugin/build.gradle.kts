// Adds compatibility { } to plugin declarations; applied by plugin-publish.
import org.gradle.plugin.compatibility.compatibility

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.plugin.publish)
    // Version comes from the root build, which aggregates buildHealth.
    id("com.autonomousapps.dependency-analysis")
}

group = "dev.balakmran.sterling"

version = providers.fileContents(layout.settingsDirectory.file("version.txt")).asText.get().trim()

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    // Puts javadoc in every build; the Plugin Portal also expects the jar.
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    // JVM 17 bytecode loads on a JDK 17 daemon, so Sterling can fail with a clear message.
    options.release = 17
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.javadoc {
    // Missing or broken docs on the public API fail the build.
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Xwerror", true)
    }
}

dependencies {
    implementation(libs.errorprone.gradle)
    implementation(libs.spotbugs.gradle)
    implementation(libs.spotless.gradle)
    implementation(libs.spotless.lib)
}

// The tool versions Sterling pins, read by the plugin at runtime (ToolVersions.java).
val generatedResources = layout.buildDirectory.dir("generated/sterling-resources")
val versionResource =
    tasks.register<WriteProperties>("generateVersionResource") {
        description = "Writes Sterling's pinned tool versions into a plugin resource."
        destinationFile = generatedResources.map {
            it.file("dev/balakmran/sterling/versions.properties")
        }
        property("sterling", version)
        property("google-java-format", libs.versions.google.java.format)
        property("spotless", libs.versions.spotless.asProvider())
        property("errorprone", libs.versions.errorprone.asProvider())
        property("nullaway", libs.versions.nullaway)
        property("jspecify", libs.versions.jspecify)
        property("spotbugs", libs.versions.spotbugs.asProvider())
        property("jacoco", libs.versions.jacoco)
    }

sourceSets.main { resources.srcDir(files(generatedResources).builtBy(versionResource)) }

// The Plugin Portal description, and the tagline the README, CLAUDE.md and package-info repeat.
val pluginDescription =
    "One code-quality gate for Java 21+ Gradle builds: google-java-format, " +
        "Error Prone, NullAway with JSpecify, SpotBugs and JaCoCo, at versions " +
        "tested together, run by stlCheck and tuned by one profile."

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit)
            dependencies {
                // The Gradle API is compile-only; ProjectBuilder needs it at runtime.
                implementation(gradleApi())
                implementation(libs.assertj)
                implementation(libs.junit.jupiter.api)
            }
            // The pin, docs and tagline tests compare the code with these files.
            targets.configureEach {
                testTask.configure {
                    val catalog = layout.settingsDirectory.file("gradle/libs.versions.toml")
                    val versionFile = layout.settingsDirectory.file("version.txt")
                    val readme = layout.settingsDirectory.file("README.md")
                    val claude = layout.settingsDirectory.file("CLAUDE.md")
                    val packageInfo =
                        layout.projectDirectory.file(
                            "src/main/java/dev/balakmran/sterling/package-info.java"
                        )
                    val guide = layout.settingsDirectory.dir("docs/guide")
                    inputs
                        .files(catalog, versionFile, readme, claude, packageInfo)
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                    inputs.dir(guide).withPathSensitivity(PathSensitivity.RELATIVE)
                    inputs.property("pluginDescription", pluginDescription)
                    systemProperty("sterling.catalog", catalog.asFile.absolutePath)
                    systemProperty("sterling.versionFile", versionFile.asFile.absolutePath)
                    systemProperty("sterling.readme", readme.asFile.absolutePath)
                    systemProperty("sterling.guide", guide.asFile.absolutePath)
                    systemProperty("sterling.claude", claude.asFile.absolutePath)
                    systemProperty("sterling.packageInfo", packageInfo.asFile.absolutePath)
                    systemProperty("sterling.description", pluginDescription)
                }
            }
        }

        register<JvmTestSuite>("functionalTest") {
            useJUnitJupiter(libs.versions.junit)
            dependencies {
                implementation(gradleTestKit())
                implementation(libs.assertj)
                implementation(libs.junit.jupiter.api)
            }
            targets.configureEach {
                testTask.configure {
                    shouldRunAfter(tasks.test)
                    // The matrix cell: -Psterling.test.gradle=9.0.0 -Psterling.test.java=25.
                    // A blank Gradle version means the wrapper's.
                    systemProperty(
                        "sterling.gradleVersion",
                        providers.gradleProperty("sterling.test.gradle").getOrElse(""),
                    )
                    systemProperty(
                        "sterling.javaVersion",
                        providers.gradleProperty("sterling.test.java").getOrElse("21"),
                    )
                    // JDK 17 checks the below-21 failures; 21 and 25 are consumer toolchains.
                    val jdks = listOf(17, 21, 25).associateWith { jdkPath(it) }
                    jvmArgumentProviders.add(
                        CommandLineArgumentProvider {
                            jdks.map { (version, path) -> "-Dsterling.jdk$version=${path.get()}" }
                        }
                    )
                    // Lets tests assert pinned versions without repeating them.
                    systemProperty(
                        "sterling.spotbugsVersion",
                        libs.versions.spotbugs.asProvider().get(),
                    )
                    systemProperty(
                        "sterling.errorproneVersion",
                        libs.versions.errorprone.asProvider().get(),
                    )
                    systemProperty("sterling.nullawayVersion", libs.versions.nullaway.get())
                    systemProperty("sterling.jspecifyVersion", libs.versions.jspecify.get())
                    systemProperty(
                        "sterling.googleJavaFormatVersion",
                        libs.versions.google.java.format.get(),
                    )
                }
            }
        }
    }
}

fun jdkPath(version: Int): Provider<String> =
    javaToolchains
        .launcherFor { languageVersion = JavaLanguageVersion.of(version) }
        .map { it.metadata.installationPath.asFile.absolutePath }

gradlePlugin {
    testSourceSets(sourceSets["functionalTest"])
    website = "https://github.com/balakmran/sterling"
    vcsUrl = "https://github.com/balakmran/sterling"
    // Search terms first, then the tools by name.
    val portalTags =
        listOf(
            "java",
            "code-quality",
            "static-analysis",
            "spring-boot",
            "google-java-format",
            "error-prone",
            "nullaway",
            "jspecify",
            "spotbugs",
            "jacoco",
        )
    plugins {
        register("sterling") {
            id = "dev.balakmran.sterling"
            implementationClass = "dev.balakmran.sterling.SterlingPlugin"
            displayName = "Sterling"
            // Every functional test runs with --configuration-cache-problems=fail.
            compatibility { features { configurationCache = true } }
            description = pluginDescription
            tags = portalTags
        }
    }
}

tasks.validatePlugins {
    enableStricterValidation = true
    failOnWarning = true
}
