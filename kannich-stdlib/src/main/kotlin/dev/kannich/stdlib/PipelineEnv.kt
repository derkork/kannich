package dev.kannich.stdlib

/**
 * Holds extra environment variables during pipeline definition time.
 *
 * WHY THIS EXISTS:
 * Users pass `-e KEY=VALUE` arguments to configure builds. During job execution,
 * these are available via JobScope.getEnv(). But pipelines are built during script
 * evaluation, BEFORE job execution. This singleton bridges that gap by allowing
 * Main.kt to set the extra env vars before script evaluation, so getEnv() calls
 * in PipelineBuilder/ExecutionBuilder/etc. can see them.
 *
 * Set by Main.kt before script evaluation, read by builder getEnv() methods.
 */
object PipelineEnv {
    private var extraEnv: Map<String, String> = emptyMap()

    /**
     * Sets the extra environment variables for pipeline definition.
     * Called by Main.kt before script evaluation.
     */
    fun setExtraEnv(env: Map<String, String>) {
        extraEnv = env
    }

    /**
     * Gets an environment variable, checking extra env first, then system env.
     * Called by builder getEnv() methods.
     */
    fun getEnv(name: String): String? = extraEnv[name] ?: System.getenv(name)

    /**
     * Clears the extra environment variables.
     * Called by Main.kt after script evaluation (optional, for safety).
     */
    fun clear() {
        extraEnv = emptyMap()
    }
}
