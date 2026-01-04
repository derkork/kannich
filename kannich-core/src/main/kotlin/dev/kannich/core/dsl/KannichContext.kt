package dev.kannich.core.dsl

import java.io.File

/**
 * Runtime context available to Kannich scripts.
 * Provides access to environment, caching, and execution state.
 */
class KannichContext(
    val cacheDir: File = defaultCacheDir()
) {
    companion object {
        private fun defaultCacheDir(): File {
            val userHome = System.getProperty("user.home")
            return File(userHome, ".kannich/cache")
        }
    }
}
