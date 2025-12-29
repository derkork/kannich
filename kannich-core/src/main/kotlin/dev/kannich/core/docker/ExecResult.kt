package dev.kannich.core.docker

/**
 * Result of executing a command in a container.
 *
 * @param stdout Standard output from the command
 * @param stderr Standard error from the command
 * @param exitCode Exit code from the command
 */
data class ExecResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
) {
    /**
     * Whether the command succeeded (exit code 0).
     */
    val success: Boolean get() = exitCode == 0
}
