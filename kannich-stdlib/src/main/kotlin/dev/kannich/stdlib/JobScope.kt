package dev.kannich.stdlib

import dev.kannich.stdlib.tools.*
import org.slf4j.LoggerFactory

/**
 * Scope available inside job blocks.
 * Provides access to shell execution and error handling utilities.
 */
@KannichDsl
class JobScope() {
    private val logger = LoggerFactory.getLogger(JobScope::class.java)

    /**
     * Shell tool for executing arbitrary commands.
     */
    val shell: ShellTool = ShellTool()

    /**
     * Filesystem tool for file/directory operations.
     */
    val fs: FsTool = FsTool()

    /**
     * Download tool for fetching files from URLs.
     */
    val download: DownloadTool = DownloadTool()

    /**
     * Extract tool for extracting archives.
     */
    val extract: ExtractTool = ExtractTool()

    /**
     * Cache tool for managing the Kannich cache.
     */
    val cache: CacheTool = CacheTool()

    /**
     * Executes a block and catches any JobFailedException.
     * Use this to continue execution even if commands fail.
     *
     * @param block The block to execute
     * @return true if the block executed successfully, false if it failed
     */
    fun allowFailure(block: () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: JobFailedException) {
            logger.warn("Allowed failure: ${e.message}")
            false
        }
    }
}

/**
 * DSL marker to prevent accidental nesting of DSL elements.
 */
@DslMarker
annotation class KannichDsl
