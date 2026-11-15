// Records each task's outcome in build/sterling-agent/outcomes.json at the build's root.
// Applied by outcomes.sh. Public Gradle API only; works with the configuration cache.
import javax.inject.Inject
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult

abstract class SterlingOutcomes :
    BuildService<SterlingOutcomes.Params>, OperationCompletionListener, AutoCloseable {
    interface Params : BuildServiceParameters {
        val output: RegularFileProperty
    }

    private val rows = java.util.concurrent.ConcurrentLinkedQueue<String>()

    override fun onFinish(event: FinishEvent) {
        if (event !is TaskFinishEvent) return
        val outcome =
            when (val result = event.result) {
                is TaskFailureResult -> "FAILED"
                is TaskSkippedResult -> if (result.skipMessage == "NO-SOURCE") "NO_SOURCE" else "SKIPPED"
                is TaskSuccessResult ->
                    when {
                        result.isFromCache -> "FROM_CACHE"
                        result.isUpToDate -> "UP_TO_DATE"
                        else -> "SUCCESS"
                    }
                else -> "UNKNOWN"
            }
        rows.add("""{"path":"${event.descriptor.taskPath}","outcome":"$outcome"}""")
    }

    override fun close() {
        val file = parameters.output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(rows.sorted().joinToString(",", "[", "]\n"))
    }
}

// A settings plugin, because Gradle documents service injection for settings and project plugins.
abstract class SterlingOutcomesPlugin
@Inject constructor(private val registry: BuildEventsListenerRegistry) : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val outcomes =
            settings.gradle.sharedServices.registerIfAbsent(
                "sterlingOutcomes",
                SterlingOutcomes::class.java,
            ) {
                parameters.output.set(
                    java.io.File(settings.rootDir, "build/sterling-agent/outcomes.json")
                )
            }
        registry.onTaskCompletion(outcomes)
    }
}

beforeSettings { pluginManager.apply(SterlingOutcomesPlugin::class.java) }
