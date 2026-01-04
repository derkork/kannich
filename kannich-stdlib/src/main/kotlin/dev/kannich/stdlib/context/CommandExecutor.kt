package dev.kannich.stdlib.context

import java.io.InputStream

/**
 * Interface for executing commands. This is the low-level API that the stdlib uses to perform actual work.
 */
interface CommandExecutor {
    /**
     * Executes a command with the given arguments.
     *
     * @param command The command and its arguments
     * @param workingDir The working directory for the command
     * @param env Environment variables to set for the command
     * @param silent If true, suppresses output logging
     * @return The result of executing the command
     */
    fun exec(
        command: List<String>,
        workingDir: String,
        env: Map<String, String>,
        silent: Boolean
    ): ExecResult

    /**
     * Writes content from an input stream to a file in the container.
     * Uses Docker's copy API for reliable streaming of large files.
     *
     * @param path The absolute path to write to
     * @param content The input stream to read content from
     * @param append If true, appends to existing file instead of overwriting
     */
    fun writeFile(path: String, content: InputStream, append: Boolean = false)
}
