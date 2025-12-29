package dev.kannich.core.execution

import dev.kannich.core.docker.ContainerManager
import dev.kannich.stdlib.context.CommandExecutor
import dev.kannich.stdlib.context.ExecResult

/**
 * CommandExecutor implementation that executes commands in a Docker container.
 */
class ContainerCommandExecutor(
    private val containerManager: ContainerManager,
    private val defaultWorkingDir: String
) : CommandExecutor {

    override fun exec(command: List<String>, workingDir: String, env: Map<String, String>, silent: Boolean): ExecResult {
        return containerManager.exec(
            command = command,
            workingDir = workingDir,
            env = env,
            silent = silent
        )
    }
}
