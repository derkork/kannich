package dev.kannich.stdlib.tools

import dev.kannich.stdlib.context.ExecResult
import dev.kannich.stdlib.fail
import dev.kannich.stdlib.secret
import org.slf4j.LoggerFactory

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
object Docker {
    private val logger = LoggerFactory.getLogger(Docker::class.java)

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
        val result = Shell.exec("docker", *args, silent = silent)
        if (!result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("Docker command failed: $errorMessage")
        }
        return result
    }

    /**
     * Logs into a Docker registry.
     * Password is passed securely via stdin and marked as secret.
     *
     * @param username The registry username
     * @param password The registry password (will be masked in logs)
     * @param registry The registry URL (defaults to Docker Hub if null)
     * @return The execution result
     * @throws dev.kannich.stdlib.JobFailedException if login fails
     */
    fun login(username: String, password: String, registry: String? = null): ExecResult {
        secret(password)
        logger.info("Logging into Docker registry with username '$username' and registry '${registry ?: "Docker Hub"}'")
        val registryArg = registry ?: ""
        val result = Shell.execShell(
            "echo \"\$DOCKER_PASSWORD\" | docker login -u \"$username\" --password-stdin $registryArg".trim(),
            env = mapOf("DOCKER_PASSWORD" to password),
            silent = true
        )

        if (!result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("Docker login failed: $errorMessage")
        }
        logger.info("Docker login successful.")
        return result
    }
}
