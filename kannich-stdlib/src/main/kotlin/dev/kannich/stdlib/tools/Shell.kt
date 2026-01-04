package dev.kannich.stdlib.tools

import dev.kannich.stdlib.context.ExecResult
import dev.kannich.stdlib.context.currentJobExecutionContext

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
     * @param env Additional environment variables (merged with job and pipeline env)
     * @param silent If true, suppresses output to the console
     * @return The execution result
     */
    suspend fun exec(
        command: String,
        vararg args: String,
        env: Map<String, String> = emptyMap(),
        silent: Boolean = false
    ): ExecResult {
        val ctx = currentJobExecutionContext()
        val fullCommand = listOf(command) + args.toList()
        val fullEnv = ctx.pipelineContext.env + getJobEnv() + env
        return ctx.executor.exec(fullCommand, ctx.workingDir, fullEnv, silent)
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
     * @param env Additional environment variables (merged with job and pipeline env)
     * @param silent If true, suppresses output to the console
     * @return The execution result
     */
    suspend fun execShell(
        command: String,
        env: Map<String, String> = emptyMap(),
        silent: Boolean = false
    ): ExecResult {
        val ctx = currentJobExecutionContext()
        val fullEnv = ctx.pipelineContext.env + getJobEnv() + env
        return ctx.executor.exec(listOf("sh", "-c", command), ctx.workingDir, fullEnv, silent)
    }
}
