package dev.kannich.stdlib.tools

import dev.kannich.stdlib.context.ExecResult
import dev.kannich.stdlib.context.currentJobContext

/**
 * Tool for executing shell commands.
 */
object Shell {
    /**
     * Executes a command with the given arguments.
     * Returns the result for inspection (stdout, stderr, exit code).
     *
     * Environment variables are merged in this order (later overrides earlier):
     * 1. Pipeline context environment
     * 2. Job-scoped environment (set via EnvTool)
     * 3. Command-specific environment (env parameter)
     *
     * @param command The command to execute
     * @param args Arguments to pass to the command
     * @param silent If true, suppresses output to the console
     * @return The execution result
     */
    suspend fun exec(
        command: String,
        vararg args: String,
        silent: Boolean = false
    ): ExecResult {
        val ctx = currentJobContext()
        val fullCommand = listOf(command) + args.toList()
        return ctx.executor.exec(fullCommand, ctx.workingDir, ctx.env, silent)
    }

    /**
     * Executes a shell command string (interpreted by sh -c).
     * Returns the result for inspection (stdout, stderr, exit code).
     *
     * Environment variables are merged in this order (later overrides earlier):
     * 1. Pipeline context environment
     * 2. Job-scoped environment (set via EnvTool)
     * 3. Command-specific environment (env parameter)
     *
     * @param command The shell command string to execute
     * @param silent If true, suppresses output to the console
     * @return The execution result
     */
    suspend fun execShell(
        command: String,
        silent: Boolean = false
    ): ExecResult {
        val ctx = currentJobContext()
        return ctx.executor.exec(listOf("sh", "-c", command), ctx.workingDir, ctx.env, silent)
    }
}
