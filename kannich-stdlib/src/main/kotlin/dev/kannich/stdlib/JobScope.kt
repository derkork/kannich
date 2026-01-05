package dev.kannich.stdlib

/**
 * Scope available inside job blocks.
 * Provides user-facing DSL for artifacts, logging, error handling, and convenience
 * methods that delegate to the underlying JobContext.
 */
@KannichDsl
class JobScope(name: String?): Logging by LoggingImpl("Job${ if (name != null) " $name" else ""}") {
    private val artifactSpecs = mutableListOf<ArtifactSpec>()

    /**
     * Returns the value of an environment variable or null if it is not set.
     */
    suspend fun getEnv(name: String): String? = JobContext.current().env[name]

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
     * Used by ExecutionEngine to collect artifacts after job execution.
     */
    fun getArtifactSpecs(): List<ArtifactSpec> = artifactSpecs.toList()

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
            logWarning("Allowed failure: ${e.message}")
            false
        }
    }

    /**
     * Changes the given environment variables for the duration of the block.
     *
     * @param vars The environment variables to set. If a variable is null, it is removed from the environment.
     * @param block The block to execute with the new environment.
     * @return The result of the block.
     */
    suspend fun <T> withEnv(vars: Map<String, String?>, block: suspend () -> T): T {
        return JobContext.current().withEnv(vars, block)
    }

    /**
     * Registers a cleanup action to run when the job completes.
     * Cleanup actions run in reverse order (last registered runs first).
     * Cleanup runs regardless of job success or failure.
     *
     * @param action The cleanup action to execute
     */
    suspend fun onCleanup(action: suspend () -> Unit) {
        JobContext.current().onCleanup(action)
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
        return JobContext.current().cd(path, block)
    }
}

/**
 * DSL marker to prevent accidental nesting of DSL elements.
 */
@DslMarker
annotation class KannichDsl
