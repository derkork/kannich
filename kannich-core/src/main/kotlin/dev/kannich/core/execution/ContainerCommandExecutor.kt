package dev.kannich.core.execution

import dev.kannich.core.docker.ContainerManager
import dev.kannich.stdlib.context.CommandExecutor
import dev.kannich.stdlib.context.ExecResult as StdlibExecResult
import dev.kannich.core.docker.ExecResult as CoreExecResult

/**
 * CommandExecutor implementation that executes commands in a Docker container.
 */
class ContainerCommandExecutor(
    private val containerManager: ContainerManager,
    private val defaultWorkingDir: String
) : CommandExecutor {

    override fun exec(command: List<String>, workingDir: String, env: Map<String, String>): StdlibExecResult {
        val coreResult = containerManager.exec(
            command = command,
            workingDir = workingDir,
            env = env,
            silent = false
        )
        return coreResult.toStdlib()
    }

    private fun CoreExecResult.toStdlib(): StdlibExecResult {
        return StdlibExecResult(
            stdout = this.stdout,
            stderr = this.stderr,
            exitCode = this.exitCode
        )
    }
}
