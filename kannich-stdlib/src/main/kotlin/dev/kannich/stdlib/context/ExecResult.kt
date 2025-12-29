package dev.kannich.stdlib.context

/**
 * Result of executing a command.
 */
data class ExecResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
) {
    val success: Boolean get() = exitCode == 0
}
