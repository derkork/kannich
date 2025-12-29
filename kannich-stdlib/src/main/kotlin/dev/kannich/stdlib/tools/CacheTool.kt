package dev.kannich.stdlib.tools

import dev.kannich.stdlib.context.JobExecutionContext
import dev.kannich.stdlib.fail

/**
 * Built-in tool for managing the Kannich cache.
 * Provides methods to store, retrieve, and clear cached items.
 *
 * Cache structure: /kannich/cache/{key}/...
 */
class CacheTool {
    private val fs = FsTool()
    private val shell = ShellTool()

    /**
     * Checks if a cache key exists.
     *
     * @param key The cache key (e.g., "java/temurin-21", "maven/apache-maven-3.9.6")
     * @return true if the cached item exists
     */
    fun exists(key: String): Boolean {
        return fs.exists(path(key))
    }

    /**
     * Gets the full path to a cached item.
     *
     * @param key The cache key
     * @return The full path to the cached item
     */
    fun path(key: String): String {
        val ctx = JobExecutionContext.current()
        return "${ctx.pipelineContext.cacheDir}/$key"
    }

    /**
     * Gets the base cache directory path.
     *
     * @return The cache directory path
     */
    fun baseDir(): String {
        val ctx = JobExecutionContext.current()
        return ctx.pipelineContext.cacheDir
    }

    /**
     * Moves a file or directory into the cache.
     *
     * @param sourcePath The source path to move from
     * @param key The cache key to move to
     * @throws dev.kannich.stdlib.JobFailedException if the move fails
     */
    fun put(sourcePath: String, key: String) {
        val cachePath = path(key)
        val parentDir = cachePath.substringBeforeLast('/')
        fs.mkdir(parentDir)
        fs.move(sourcePath, cachePath)
    }

    /**
     * Clears a specific cache key or the entire cache.
     *
     * @param key The cache key to clear, or null to clear the entire cache
     * @throws dev.kannich.stdlib.JobFailedException if clearing fails
     */
    fun clear(key: String? = null) {
        if (key != null) {
            fs.delete(path(key))
        } else {
            // Clear all contents but keep the cache directory itself
            val result = shell.execShell("rm -rf ${baseDir()}/*")
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
    fun ensureDir(key: String) {
        fs.mkdir(path(key))
    }
}
