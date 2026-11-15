// Prints one line per project for detect.sh, prefixed "STERLING_DETECT ". Read-only: it registers
// one task that does nothing, and reads plugins, toolchains, declared dependencies and sources.
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification


fun json(value: Any?): String =
    when (value) {
        null -> "null"
        is Boolean, is Int -> value.toString()
        is String ->
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { "${json(it.key)}:${json(it.value)}" }
        is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
        else -> json(value.toString())
    }

val pluginIds =
    listOf(
        "java",
        "dev.balakmran.sterling",
        "com.diffplug.spotless",
        "net.ltgt.errorprone",
        "com.github.spotbugs",
        "com.github.spotbugs-base",
        "jacoco",
        "checkstyle",
        "pmd",
        "org.springframework.boot",
        "io.spring.nullability",
    )

val watchedModules =
    mapOf(
        "org.projectlombok:lombok" to "lombok",
        "org.mapstruct:mapstruct-processor" to "mapstruct",
        "com.google.errorprone:error_prone_core" to "errorProne",
        "com.uber.nullaway:nullaway" to "nullAway",
        "org.jspecify:jspecify" to "jspecify",
    )

gradle.projectsEvaluated {
    // Init scripts also run in included builds such as build-logic; describe the main build only.
    if (gradle.parent != null) return@projectsEvaluated
    rootProject.tasks.register("sterlingDetect")
    allprojects {
        val project = this
        val info = linkedMapOf<String, Any?>("path" to project.path)
        info["plugins"] = pluginIds.filter { project.pluginManager.hasPlugin(it) }

        val declared = linkedMapOf<String, String?>()
        project.configurations.forEach { configuration ->
            configuration.dependencies.forEach { dependency ->
                watchedModules["${dependency.group}:${dependency.name}"]?.let {
                    declared[it] = dependency.version
                }
            }
        }
        info["dependencies"] = declared

        if (project.pluginManager.hasPlugin("java")) {
            val java = project.extensions.getByType(JavaPluginExtension::class.java)
            info["toolchain"] = java.toolchain.languageVersion.orNull?.asInt()
            val main = java.sourceSets.getByName("main")
            val buildDir = project.layout.buildDirectory.get().asFile
            val dirs = main.java.srcDirs
            info["generatedSourceDirs"] =
                dirs.filter { it.startsWith(buildDir) }.map { it.relativeTo(project.projectDir).path }
            val sources =
                dirs.filterNot { it.startsWith(buildDir) }
                    .filter { it.isDirectory }
                    .flatMap { dir -> dir.walk().filter { it.isFile && it.name.endsWith(".java") }.toList() }
            info["javaSources"] = sources.size
            info["nullMarkedPackages"] =
                sources.count { it.name == "package-info.java" && it.readText().contains("@NullMarked") }
            // The leading spaces of the first indented line in each file: 4 means AOSP-like code.
            val indents =
                sources.take(200).mapNotNull { file ->
                    file.useLines { lines ->
                        lines.firstOrNull { it.startsWith(" ") && it.isNotBlank() }
                            ?.takeWhile { it == ' ' }
                            ?.length
                    }
                }
            info["fourSpaceIndentShare"] =
                if (indents.isEmpty()) 0 else indents.count { it == 4 } * 100 / indents.size
            val rules =
                project.tasks
                    .withType(JacocoCoverageVerification::class.java)
                    .flatMap { task ->
                        task.violationRules.rules.flatMap { rule ->
                            rule.limits.map { "${it.counter}:${it.value}:${it.minimum}" }
                        }
                    }
            info["jacocoRules"] = rules
        }
        println("STERLING_DETECT " + json(info))
    }
}
