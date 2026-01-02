package dev.kannich.stdlib.tools

/**
 * Read-only environment variable accessor.
 *
 * This class provides a consistent API for reading environment variables
 * at pipeline and execution definition time. It wraps a map of environment
 * variables and provides the same read methods as EnvTool.
 *
 * Usage in pipeline/execution blocks:
 * ```kotlin
 * pipeline {
 *     if (env.isSet("DEVMODE")) {
 *         // define devmode-specific jobs
 *     }
 *
 *     val buildType = env.get("BUILD_TYPE", "release")
 *
 *     execution("build") {
 *         if (env["WITH_TESTS"] == "true") {
 *             job(test)
 *         }
 *     }
 * }
 * ```
 *
 * Note: This class is read-only. For mutable environment variables within
 * job execution, use EnvTool instead.
 */
class Env(private val backing: Map<String, String>) {

    /**
     * Gets an environment variable value with a default.
     *
     * @param name The environment variable name
     * @param default The default value if not set (defaults to empty string)
     * @return The value, or the default if not set
     */
    fun get(name: String, default: String = ""): String = backing[name] ?: default

    /**
     * Gets an environment variable value using index operator.
     *
     * @param name The environment variable name
     * @return The value, or null if not set
     */
    operator fun get(name: String): String? = backing[name]

    /**
     * Checks if an environment variable is set.
     *
     * @param name The environment variable name
     * @return true if the variable is set, false otherwise
     */
    fun isSet(name: String): Boolean = backing.containsKey(name)

    /**
     * Gets all environment variables.
     *
     * @return A map of all environment variables
     */
    fun getAll(): Map<String, String> = backing.toMap()
}
