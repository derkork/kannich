package dev.kannich.stdlib

import dev.kannich.stdlib.context.ExecResult
import dev.kannich.stdlib.context.JobExecutionContext
import org.slf4j.LoggerFactory

/**
 * Scope available inside job blocks.
 * Provides access to shell execution and error handling utilities.
 */
@KannichDsl
class JobScope(
    private val ctx: JobExecutionContext
) {
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
 * Tool for executing shell commands.
 */
class ShellTool {
    /**
     * Executes a command with the given arguments.
     * Returns the result for inspection (stdout, stderr, exit code).
     *
     * @param command The command to execute
     * @param args Arguments to pass to the command
     * @return The execution result
     */
    fun exec(command: String, vararg args: String): ExecResult {
        val ctx = JobExecutionContext.current()
        val fullCommand = listOf(command) + args.toList()
        return ctx.executor.exec(fullCommand, ctx.workingDir, emptyMap(), false)
    }

    /**
     * Executes a shell command string (interpreted by sh -c).
     * Returns the result for inspection (stdout, stderr, exit code).
     *
     * @param command The shell command string to execute
     * @return The execution result
     */
    fun execShell(command: String): ExecResult {
        val ctx = JobExecutionContext.current()
        return ctx.executor.exec(listOf("sh", "-c", command), ctx.workingDir, emptyMap(), false)
    }
}

/**
 * DSL marker to prevent accidental nesting of DSL elements.
 */
@DslMarker
annotation class KannichDsl
