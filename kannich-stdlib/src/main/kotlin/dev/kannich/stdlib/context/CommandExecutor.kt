package dev.kannich.stdlib.context

/**
 * Interface for executing commands.
 * Implemented by kannich-core to execute commands in containers.
 */
interface CommandExecutor {
    /**
     * Executes a command with the given arguments.
     *
     * @param command The command and its arguments
     * @param workingDir The working directory for the command
     * @param env Environment variables to set for the command
     * @return The result of executing the command
     */
    fun exec(command: List<String>, workingDir: String, env: Map<String, String>, silent: Boolean): ExecResult
}
