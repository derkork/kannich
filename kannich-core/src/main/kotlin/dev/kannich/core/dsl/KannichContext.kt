package dev.kannich.core.dsl

import java.io.File

/**
 * Runtime context available to Kannich scripts.
 * Provides access to environment, caching, and execution state.
 */
class KannichContext(
    val projectDir: File,
    val cacheDir: File = defaultCacheDir()
) {
    // Environment variables available to the build
    val env: Map<String, String> = System.getenv()

    // Properties that can be set from command line
    private val properties = mutableMapOf<String, String>()

    fun property(name: String): String? = properties[name] ?: System.getProperty(name)

    internal fun setProperty(name: String, value: String) {
        properties[name] = value
    }

    companion object {
        private fun defaultCacheDir(): File {
            val userHome = System.getProperty("user.home")
            return File(userHome, ".kannich/cache")
        }
    }
}
