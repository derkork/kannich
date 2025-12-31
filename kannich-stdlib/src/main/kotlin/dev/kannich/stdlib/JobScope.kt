package dev.kannich.stdlib

import dev.kannich.stdlib.tools.*
import org.slf4j.LoggerFactory

/**
 * Scope available inside job blocks.
 * Provides access to shell execution and error handling utilities.
 */
@KannichDsl
class JobScope private constructor() {
    private val logger = LoggerFactory.getLogger(JobScope::class.java)
    private val cleanupActions = mutableListOf<() -> Unit>()
    private val artifactSpecs = mutableListOf<ArtifactSpec>()

    /**
     * Shell tool for executing arbitrary commands.
     */
    val shell: ShellTool = ShellTool()

    /**
     * Filesystem tool for file/directory operations.
     */
    val fs: FsTool = FsTool()

    /**
     * Download tool for fetching files from URLs.
     */
    val download: DownloadTool = DownloadTool()

    /**
     * Extract tool for extracting archives.
     */
    val extract: ExtractTool = ExtractTool()

    /**
     * Cache tool for managing the Kannich cache.
     */
    val cache: CacheTool = CacheTool()

    /**
     * APT tool for installing system packages with caching.
     */
    val apt: AptTool = AptTool()

    /**
     * Docker tool for executing docker commands.
     */
    val docker: DockerTool = DockerTool()

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
    fun allowFailure(block: () -> Unit): Boolean {
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
    fun onCleanup(action: () -> Unit) {
        cleanupActions.add(action)
    }

    /**
     * Runs all registered cleanup actions in reverse order.
     * Exceptions during cleanup are logged but don't prevent other cleanups from running.
     */
    internal fun runCleanup() {
        cleanupActions.asReversed().forEach { action ->
            runCatching { action() }.onFailure { e ->
                logger.warn("Cleanup action failed: ${e.message}")
            }
        }
    }

    companion object {
        private val current = ThreadLocal<JobScope>()

        /**
         * Gets the current JobScope.
         * @throws IllegalStateException if called outside a job block
         */
        fun current(): JobScope = current.get()
            ?: error("No JobScope available - must be called from within a job block")

        /**
         * Executes a block with a new JobScope, ensuring cleanup runs on completion.
         * This is used by the execution engine to wrap job execution.
         *
         * @return A [JobScopeResult] containing the block's return value and collected artifacts
         */
        fun <T> withScope(block: JobScope.() -> T): JobScopeResult<T> {
            val scope = JobScope()
            val prev = current.get()
            current.set(scope)
            return try {
                val result = scope.block()
                JobScopeResult(result, scope.getArtifactSpecs())
            } finally {
                scope.runCleanup()
                EnvTool.clearJobEnv()
                if (prev != null) current.set(prev) else current.remove()
            }
        }
    }
}

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
