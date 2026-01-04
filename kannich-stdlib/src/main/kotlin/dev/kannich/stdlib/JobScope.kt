package dev.kannich.stdlib

import dev.kannich.stdlib.context.JobExecutionContext
import dev.kannich.stdlib.context.currentJobExecutionContext
import dev.kannich.stdlib.tools.*
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Scope available inside job blocks.
 * Provides access to shell execution and error handling utilities.
 *
 * Access the current scope via [currentJobScope] suspend function.
 */
@KannichDsl
class JobScope private constructor(name: String) : CoroutineContext.Element {
    private val logger = LoggerFactory.getLogger(JobScope::class.java)
    private val cleanupActions = mutableListOf<suspend () -> Unit>()
    private val artifactSpecs = mutableListOf<ArtifactSpec>()

    /**
     * Logger for this job scope. Can be used in kannichfile to print logs.
     */
    val log = LoggerFactory.getLogger("dev.kannich.jobs.Job $name")

    /**
     * Environment tool for reading and writing environment variables.
     */
    val env: EnvTool = EnvTool()

    /**
     * Collects artifacts matching the specified patterns.
     * Can be called multiple times during job execution - all patterns are accumulated.
     *
     * Pattern syntax (ant-style):
     * - `*` matches 0 or more characters except the directory separator (`/`)
     * - `**` matches 0 or more characters including the directory separator (`/`)
     * - `?` matches a single character
     *
     * Only files within the workspace directory can be collected as artifacts.
     */
    fun artifacts(block: ArtifactSpecBuilder.() -> Unit) {
        val spec = ArtifactSpecBuilder().apply(block).build()
        artifactSpecs.add(spec)
    }

    /**
     * Returns all collected artifact specifications.
     */
    internal fun getArtifactSpecs(): List<ArtifactSpec> = artifactSpecs.toList()

    /**
     * Executes a block and catches any JobFailedException.
     * Use this to continue execution even if commands fail.
     *
     * @param block The block to execute
     * @return true if the block executed successfully, false if it failed
     */
    suspend fun allowFailure(block: suspend () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: JobFailedException) {
            logger.warn("Allowed failure: ${e.message}")
            false
        }
    }

    /**
     * Registers a cleanup action to run when the job completes.
     * Cleanup actions run in reverse order (last registered runs first).
     * Cleanup runs regardless of job success or failure.
     *
     * @param action The cleanup action to execute
     */
    fun onCleanup(action: suspend () -> Unit) {
        cleanupActions.add(action)
    }

    /**
     * Changes the working directory for the duration of the block.
     * The path is relative to the current working directory.
     * After the block completes, the previous working directory is restored.
     *
     * Can be nested to navigate deeper into directory structures:
     * ```
     * cd("some/path") {
     *     cd("nested") {
     *         // Working in some/path/nested
     *     }
     *     // Back in some/path
     * }
     * ```
     *
     * @param path The relative path to change to
     * @param block The block to execute in the new directory
     * @return The result of the block
     * @throws JobFailedException if the directory does not exist
     */
    suspend fun <T> cd(path: String, block: suspend () -> T): T {
        val currentCtx = currentJobExecutionContext()
        val newWorkingDir = "${currentCtx.workingDir}/$path"

        // Check if directory exists before changing to it
        val checkResult = currentCtx.executor.exec(
            command = listOf("test", "-d", newWorkingDir),
            workingDir = currentCtx.workingDir,
            env = emptyMap(),
            silent = true
        )
        if (!checkResult.success) {
            fail("Directory '$path' does not exist (full path: $newWorkingDir)")
        }

        val newCtx = JobExecutionContext(
            pipelineContext = currentCtx.pipelineContext,
            executor = currentCtx.executor,
            workingDir = newWorkingDir
        )
        return withContext(newCtx) {
            block()
        }
    }

    /**
     * Runs all registered cleanup actions in reverse order.
     * Exceptions during cleanup are logged but don't prevent other cleanups from running.
     */
    internal suspend fun runCleanup() {
        cleanupActions.asReversed().forEach { action ->
            runCatching { action() }.onFailure { e ->
                logger.warn("Cleanup action failed: ${e.message}")
            }
        }
    }

    override val key: CoroutineContext.Key<JobScope> get() = Key

    companion object Key : CoroutineContext.Key<JobScope> {
        /**
         * Executes a block with a new JobScope, ensuring cleanup runs on completion.
         * This is used by the execution engine to wrap job execution.
         *
         * @return A [JobScopeResult] containing the block's return value and collected artifacts
         */
        suspend fun <T> withScope(name: String, block: suspend JobScope.() -> T): JobScopeResult<T> {
            val scope = JobScope(name)
            return try {
                withContext(scope) {
                    val result = scope.block()
                    JobScopeResult(result, scope.getArtifactSpecs())
                }
            } finally {
                scope.runCleanup()
                clearJobEnv()
            }
        }
    }
}

/**
 * Gets the current JobScope.
 * @throws IllegalStateException if called outside a job block
 */
suspend fun currentJobScope(): JobScope =
    coroutineContext[JobScope]
        ?: error("No JobScope available - must be called from within a job block")

/**
 * Result from executing a job scope block, including collected artifacts.
 */
data class JobScopeResult<T>(
    val result: T,
    val artifacts: List<ArtifactSpec>
)

/**
 * DSL marker to prevent accidental nesting of DSL elements.
 */
@DslMarker
annotation class KannichDsl
