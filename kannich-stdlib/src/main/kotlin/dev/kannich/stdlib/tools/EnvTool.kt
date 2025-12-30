package dev.kannich.stdlib.tools

import dev.kannich.stdlib.context.JobExecutionContext

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
        private val jobEnv = ThreadLocal<MutableMap<String, String>>()

        /**
         * Gets the current job's mutable environment map.
         * Creates one if it doesn't exist.
         */
        internal fun getJobEnv(): MutableMap<String, String> {
            var env = jobEnv.get()
            if (env == null) {
                env = mutableMapOf()
                jobEnv.set(env)
            }
            return env
        }

        /**
         * Clears the job environment. Called when a job completes.
         */
        internal fun clearJobEnv() {
            jobEnv.remove()
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
