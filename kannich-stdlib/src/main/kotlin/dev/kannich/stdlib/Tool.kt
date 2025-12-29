package dev.kannich.stdlib

import dev.kannich.stdlib.context.ExecResult
import dev.kannich.stdlib.context.JobExecutionContext

/**
 * Exception thrown when a job execution fails.
 */
class JobFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Throws a JobFailedException with the given message.
 *
 * @param message The error message
 * @throws JobFailedException always
 */
fun fail(message: String): Nothing {
    throw JobFailedException(message)
}

/**
 * Interface for tools that can be installed and executed in jobs.
 * Examples: Java SDK, Maven, Gradle, etc.
 */
interface Tool {
    /**
     * The version of the tool.
     */
    val version: String

    /**
     * Gets the home directory for this tool.
     * The path is computed based on the context's cache directory.
     *
     * @param ctx The job execution context
     * @return The path to the tool's home directory inside the container
     */
    fun home(ctx: JobExecutionContext): String

    /**
     * Ensures the tool is installed in the cache.
     * Downloads and installs if not already present.
     *
     * @param ctx The job execution context
     */
    fun ensureInstalled(ctx: JobExecutionContext)

    /**
     * Executes the tool with the given arguments.
     * Throws JobFailedException if the execution fails.
     *
     * @param args Arguments to pass to the tool
     * @throws JobFailedException if the command fails
     */
    fun exec(vararg args: String)
}

/**
 * Base implementation of Tool with common functionality.
 */
abstract class BaseTool(override val version: String) : Tool {

    override fun exec(vararg args: String) {
        val ctx = JobExecutionContext.current()
        ensureInstalled(ctx)
        val result = doExec(ctx, *args)
        if (!result.success) {
            val errorMessage = if (result.stderr.isNotBlank()) {
                result.stderr
            } else {
                "Exit code: ${result.exitCode}"
            }
            fail("Command failed: $errorMessage")
        }
    }

    /**
     * Actually executes the tool command.
     * Called after ensureInstalled.
     *
     * @param ctx The job execution context
     * @param args Arguments to pass to the tool
     * @return The result of executing the command
     */
    protected abstract fun doExec(ctx: JobExecutionContext, vararg args: String): ExecResult
}
