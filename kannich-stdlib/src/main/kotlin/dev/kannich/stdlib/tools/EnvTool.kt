package dev.kannich.stdlib.tools

import dev.kannich.stdlib.context.JobExecutionContext
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine-aware holder for job-scoped environment variables.
 * Implements CopyableThreadContextElement to preserve the environment map
 * across coroutine suspension points.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class JobEnvContext(
    val env: MutableMap<String, String> = mutableMapOf()
) : CopyableThreadContextElement<JobEnvContext?> {

    companion object Key : CoroutineContext.Key<JobEnvContext> {
        internal val threadLocal = ThreadLocal<JobEnvContext>()
    }

    override val key: CoroutineContext.Key<JobEnvContext> get() = Key

    override fun updateThreadContext(context: CoroutineContext): JobEnvContext? {
        val prev = threadLocal.get()
        threadLocal.set(this)
        return prev
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: JobEnvContext?) {
        if (oldState != null) {
            threadLocal.set(oldState)
        } else {
            threadLocal.remove()
        }
    }

    override fun copyForChild(): CopyableThreadContextElement<JobEnvContext?> = this

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext =
        overwritingElement
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
    companion object {
        /**
         * Gets the current job's mutable environment map.
         * Creates one if it doesn't exist.
         */
        internal fun getJobEnv(): MutableMap<String, String> {
            var ctx = JobEnvContext.threadLocal.get()
            if (ctx == null) {
                ctx = JobEnvContext()
                JobEnvContext.threadLocal.set(ctx)
            }
            return ctx.env
        }

        /**
         * Clears the job environment. Called when a job completes.
         */
        internal fun clearJobEnv() {
            JobEnvContext.threadLocal.remove()
        }

        /**
         * Gets the current JobEnvContext for use as a coroutine context element.
         * Returns null if no job environment is set.
         */
        fun getJobEnvContext(): JobEnvContext? = JobEnvContext.threadLocal.get()

        /**
         * Sets up a new job environment context.
         * Returns the context for use with coroutines.
         */
        fun createJobEnvContext(): JobEnvContext {
            val ctx = JobEnvContext()
            JobEnvContext.threadLocal.set(ctx)
            return ctx
        }
    }

    /**
     * Gets an environment variable value.
     * First checks job-scoped variables, then falls back to pipeline context.
     *
     * @param name The environment variable name
     * @param default The default value if not set (defaults to empty string)
     * @return The value, or the default if not set
     */
    fun get(name: String, default: String = ""): String {
        // First check job-scoped env
        val jobValue = getJobEnv()[name]
        if (jobValue != null) {
            return jobValue
        }
        // Fall back to pipeline context
        return JobExecutionContext.current().pipelineContext.env[name] ?: default
    }

    /**
     * Checks if an environment variable is set.
     * Checks both job-scoped variables and pipeline context.
     *
     * @param name The environment variable name
     * @return true if the variable is set, false otherwise
     */
    fun isSet(name: String): Boolean {
        return getJobEnv().containsKey(name) ||
                JobExecutionContext.current().pipelineContext.env.containsKey(name)
    }

    /**
     * Sets an environment variable for this job.
     * The variable will be available to all subsequent shell commands.
     *
     * @param name The environment variable name
     * @param value The value to set
     */
    operator fun set(name: String, value: String) {
        getJobEnv()[name] = value
    }

    /**
     * Gets an environment variable value using index operator.
     * First checks job-scoped variables, then falls back to pipeline context.
     *
     * @param name The environment variable name
     * @return The value, or null if not set
     */
    operator fun get(name: String): String? {
        val jobValue = getJobEnv()[name]
        if (jobValue != null) {
            return jobValue
        }
        return JobExecutionContext.current().pipelineContext.env[name]
    }

    /**
     * Removes an environment variable from the job scope.
     * Note: This doesn't affect pipeline-level environment variables.
     *
     * @param name The environment variable name to remove
     */
    fun remove(name: String) {
        getJobEnv().remove(name)
    }

    /**
     * Gets all environment variables (job-scoped merged with pipeline context).
     * Job-scoped variables take precedence over pipeline context.
     *
     * @return A map of all environment variables
     */
    fun getAll(): Map<String, String> {
        val pipelineEnv = JobExecutionContext.current().pipelineContext.env
        return pipelineEnv + getJobEnv()
    }
}
