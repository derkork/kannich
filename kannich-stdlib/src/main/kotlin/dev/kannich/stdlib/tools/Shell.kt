package dev.kannich.stdlib.tools

import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.fail
import dev.kannich.stdlib.util.ExecResult
import dev.kannich.stdlib.util.ProcessUtil

/**
 * Tool for executing shell commands.
 */
object Shell {
    /**
     * Executes a command with the given arguments.
     * Returns the result for inspection (stdout, stderr, exit code).
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
        val ctx = JobContext.current()
        val fullCommand = listOf(command) + args.toList()
        return ProcessUtil.exec(fullCommand, ctx.workingDir, ctx.env, silent).getOrElse {
            fail("Failed to execute command: $command ${args.joinToString(" ")}")
        }
    }

    /**
     * Executes a shell command string (interpreted by sh -c).
     * Returns the result for inspection (stdout, stderr, exit code).
     *
     * @param command The shell command string to execute
     * @param silent If true, suppresses output to the console
     * @return The execution result
     */
    suspend fun execShell(
        command: String,
        silent: Boolean = false
    ): ExecResult {
        val ctx = JobContext.current()
        return ProcessUtil.execShell(command, ctx.workingDir, ctx.env, silent).getOrElse {
            fail("Failed to execute shell command: $command")
        }
    }
}
