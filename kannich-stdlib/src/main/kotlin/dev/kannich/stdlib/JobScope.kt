package dev.kannich.stdlib

import dev.kannich.stdlib.context.JobExecutionContext

/**
 * Scope available inside job blocks.
 * Provides access to shell execution and error handling utilities.
 */
@KannichDsl
class JobScope(
    private val ctx: JobExecutionContext
) {
    /**
     * Shell tool for executing arbitrary commands.
     */
    val shell: ShellTool = ShellTool()

    /**
     * Executes a block and catches any JobFailedException.
     * Use this to continue execution even if commands fail.
     *
     * @param block The block to execute
     */
    fun allowFailure(block: () -> Unit) {
        try {
            block()
        } catch (e: JobFailedException) {
            // Failure is allowed, continue execution
            System.err.println("[WARN] Allowed failure: ${e.message}")
        }
    }
}

/**
 * Tool for executing shell commands.
 */
class ShellTool {
    /**
     * Executes a shell command with the given arguments.
     * Throws JobFailedException if the command fails.
     *
     * @param command The command to execute
     * @param args Arguments to pass to the command
     * @throws JobFailedException if the command fails
     */
    fun exec(command: String, vararg args: String) {
        val ctx = JobExecutionContext.current()
        val fullCommand = listOf(command) + args.toList()
        val result = ctx.executor.exec(fullCommand, ctx.workingDir, emptyMap())
        if (!result.success) {
            val errorMessage = if (result.stderr.isNotBlank()) {
                result.stderr
            } else {
                "Exit code: ${result.exitCode}"
            }
            throw JobFailedException("Shell command failed: $command - $errorMessage")
        }
    }

    /**
     * Executes a shell command string (interpreted by sh -c).
     * Throws JobFailedException if the command fails.
     *
     * @param command The shell command string to execute
     * @throws JobFailedException if the command fails
     */
    fun execShell(command: String) {
        val ctx = JobExecutionContext.current()
        val result = ctx.executor.exec(listOf("sh", "-c", command), ctx.workingDir, emptyMap())
        if (!result.success) {
            val errorMessage = if (result.stderr.isNotBlank()) {
                result.stderr
            } else {
                "Exit code: ${result.exitCode}"
            }
            throw JobFailedException("Shell command failed: $command - $errorMessage")
        }
    }
}

/**
 * DSL marker to prevent accidental nesting of DSL elements.
 */
@DslMarker
annotation class KannichDsl
