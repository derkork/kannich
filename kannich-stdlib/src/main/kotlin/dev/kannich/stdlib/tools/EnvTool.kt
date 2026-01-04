package dev.kannich.stdlib.tools

import dev.kannich.stdlib.context.currentJobExecutionContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Coroutine context holder for job-scoped environment variables.
 * Access via coroutine context using [currentJobEnvContext] or [currentJobEnvContextOrNull].
 */
class JobEnvContext(
    val env: MutableMap<String, String> = mutableMapOf()
) : CoroutineContext.Element {

    companion object Key : CoroutineContext.Key<JobEnvContext>

    override val key: CoroutineContext.Key<JobEnvContext> get() = Key
}

/**
 * Gets the current job environment context.
 * @throws IllegalStateException if no context is available
 */
suspend fun currentJobEnvContext(): JobEnvContext =
    coroutineContext[JobEnvContext]
        ?: error("No JobEnvContext available. Are you inside a job block?")

/**
 * Gets the current job environment context, or null if not available.
 */
suspend fun currentJobEnvContextOrNull(): JobEnvContext? =
    coroutineContext[JobEnvContext]

/**
 * Gets the job environment map, or an empty map if no context is available.
 */
suspend fun getJobEnv(): Map<String, String> =
    currentJobEnvContextOrNull()?.env ?: emptyMap()

/**
 * Clears the job environment. Called when a job completes.
 * This is a no-op since the context is scoped to the coroutine.
 */
fun clearJobEnv() {
    // No-op: context is automatically scoped to the coroutine
}

/**
 * Tool for managing environment variables within a job.
 *
 * Environment variables set via this tool are available to all subsequent
 * shell commands in the same job (via ShellTool, and by extension all tools
 * that use ShellTool).
 *
 * Usage in job blocks:
 * ```kotlin
 * job("Build") {
 *     // Set an environment variable
 *     env["BUILD_VERSION"] = "1.0.0"
 *
 *     // Read an environment variable (from job or pipeline context)
 *     val version = env["BUILD_VERSION"]
 *
 *     // These will now have BUILD_VERSION available
 *     maven.exec("package")
 *     shell.exec("echo", "Building version $version")
 * }
 * ```
 */
class EnvTool {
    /**
     * Gets an environment variable value.
     * First checks job-scoped variables, then falls back to pipeline context.
     *
     * @param name The environment variable name
     * @param default The default value if not set (defaults to empty string)
     * @return The value, or the default if not set
     */
    suspend fun get(name: String, default: String = ""): String {
        // First check job-scoped env
        val jobEnv = currentJobEnvContextOrNull()?.env
        val jobValue = jobEnv?.get(name)
        if (jobValue != null) {
            return jobValue
        }
        // Fall back to pipeline context
        return currentJobExecutionContext().pipelineContext.env[name] ?: default
    }

    /**
     * Checks if an environment variable is set.
     * Checks both job-scoped variables and pipeline context.
     *
     * @param name The environment variable name
     * @return true if the variable is set, false otherwise
     */
    suspend fun isSet(name: String): Boolean {
        val jobEnv = currentJobEnvContextOrNull()?.env
        return jobEnv?.containsKey(name) == true ||
                currentJobExecutionContext().pipelineContext.env.containsKey(name)
    }

    /**
     * Gets an environment variable value using index operator.
     * First checks job-scoped variables, then falls back to pipeline context.
     *
     * @param name The environment variable name
     * @return The value, or null if not set
     */
    suspend operator fun get(name: String): String? {
        val jobEnv = currentJobEnvContextOrNull()?.env
        val jobValue = jobEnv?.get(name)
        if (jobValue != null) {
            return jobValue
        }
        return currentJobExecutionContext().pipelineContext.env[name]
    }

    /**
     * Sets an environment variable using index operator.
     * The variable will be available to all subsequent shell commands.
     *
     * @param name The environment variable name
     * @param value The value to set
     */
    suspend operator fun set(name: String, value: String) {
        currentJobEnvContext().env[name] = value
    }

    /**
     * Removes an environment variable from the job scope.
     * Note: This doesn't affect pipeline-level environment variables.
     *
     * @param name The environment variable name to remove
     */
    suspend fun remove(name: String) {
        currentJobEnvContextOrNull()?.env?.remove(name)
    }

    /**
     * Gets all environment variables (job-scoped merged with pipeline context).
     * Job-scoped variables take precedence over pipeline context.
     *
     * @return A map of all environment variables
     */
    suspend fun getAll(): Map<String, String> {
        val pipelineEnv = currentJobExecutionContext().pipelineContext.env
        val jobEnv = currentJobEnvContextOrNull()?.env ?: emptyMap()
        return pipelineEnv + jobEnv
    }
}
