package dev.kannich.stdlib.tools

import dev.kannich.stdlib.Kannich
import dev.kannich.stdlib.fail

/**
 * Built-in tool for managing the Kannich cache.
 * Provides methods to store, retrieve, and clear cached items.
 *
 * Cache structure: /kannich/cache/{key}/...
 */
object Cache {

    /**
     * Checks if a cache key exists.
     *
     * @param key The cache key (e.g., "java/temurin-21", "maven/apache-maven-3.9.6")
     * @return true if the cached item exists
     */
    suspend fun exists(key: String): Boolean {
        return Fs.exists(path(key))
    }

    /**
     * Gets the full path to a cached item.
     *
     * @param key The cache key
     * @return The full path to the cached item
     */
    suspend fun path(key: String): String {
        return "${Kannich.CACHE_DIR}/$key"
    }

    /**
     * Gets the base cache directory path.
     *
     * @return The cache directory path
     */
    suspend fun baseDir(): String {
       return Kannich.CACHE_DIR
    }

    /**
     * Clears a specific cache key or the entire cache.
     *
     * @param key The cache key to clear, or null to clear the entire cache
     * @throws dev.kannich.stdlib.JobFailedException if clearing fails
     */
    suspend fun clear(key: String? = null) {
        if (key != null) {
            Fs.delete(path(key))
        } else {
            // Clear all contents but keep the cache directory itself
            val result = Shell.execShell("rm -rf ${baseDir()}/*")
            if (!result.success) {
                fail("Failed to clear cache: ${result.stderr}")
            }
        }
    }

    /**
     * Ensures a directory exists in the cache.
     *
     * @param key The cache key (directory path relative to cache root)
     * @throws dev.kannich.stdlib.JobFailedException if directory creation fails
     */
    suspend fun ensureDir(key: String) {
        Fs.mkdir(path(key))
    }
}
