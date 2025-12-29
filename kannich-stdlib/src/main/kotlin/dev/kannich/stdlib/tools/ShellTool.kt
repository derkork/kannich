package dev.kannich.stdlib.tools

import dev.kannich.stdlib.context.ExecResult
import dev.kannich.stdlib.context.JobExecutionContext

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
