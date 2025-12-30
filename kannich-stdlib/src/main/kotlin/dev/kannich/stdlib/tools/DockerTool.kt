package dev.kannich.stdlib.tools

import dev.kannich.stdlib.context.ExecResult
import dev.kannich.stdlib.fail

/**
 * Tool for executing Docker commands.
 * Provides a simple wrapper around the docker CLI.
 *
 * Usage in job blocks:
 * ```kotlin
 * job("Build Image") {
 *     docker.exec("build", "-t", "myapp:latest", ".")
 *     docker.exec("push", "myapp:latest")
 * }
 * ```
 */
class DockerTool {
    private val shell = ShellTool()

    /**
     * Executes a docker command with the given arguments.
     * Fails if the command exits with non-zero status.
     *
     * @param args Arguments to pass to docker
     * @param silent If true, suppresses output to the console
     * @return The execution result
     * @throws dev.kannich.stdlib.JobFailedException if the command fails
     */
    fun exec(vararg args: String, silent: Boolean = false): ExecResult {
        val result = shell.exec("docker", *args, silent = silent)
        if (!result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("Docker command failed: $errorMessage")
        }
        return result
    }
}
