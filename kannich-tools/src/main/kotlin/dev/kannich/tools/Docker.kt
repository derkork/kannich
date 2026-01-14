package dev.kannich.tools

import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.FsUtil
import dev.kannich.stdlib.fail
import dev.kannich.stdlib.secret
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Tool for executing Docker commands.
 * Provides a simple wrapper around the docker CLI.
 *
 * Usage in job blocks:
 * ```kotlin
 * job("Build Image") {
 *     Docker.enable()
 *     Docker.exec("build", "-t", "myapp:latest", ".")
 *     Docker.exec("push", "myapp:latest")
 * }
 * ```
 */
object Docker {
    private val logger = LoggerFactory.getLogger(Docker::class.java)

    suspend fun isEnabled(): Boolean = Shell.execShell("docker info", silent = true).success

    /**
     * Enables docker support in the container. As starting docker takes a few seconds this is
     * done only when needed.
     */
    suspend fun enable(daemonJson:String = "") {
        val dockerRunning = isEnabled()

        val daemonJsonContent = FsUtil.readAsString(Path.of("/etc/docker/daemon.json")).getOrElse {""}
        if (daemonJsonContent != daemonJson) {
            if (dockerRunning) {
                val result = Shell.execShell("supervisorctl stop dockerd", silent = true)
                if (!result.success) {
                    fail("Failed to stop Docker daemon: ${result.stderr}")
                }
            }

            if (daemonJson.isBlank()) {
                logger.info("Removing daemon.json.")
                FsUtil.delete(Path.of("/etc/docker/daemon.json")).getOrElse {
                    fail("Failed to delete daemon.json")
                }
            }
            else {
                logger.info("Updating daemon.json.")
                FsUtil.write(Path.of("/etc/docker/daemon.json"), daemonJson).getOrElse {
                    fail("Failed to write daemon.json")
                }
            }

            if (dockerRunning) {
                val result = Shell.execShell("supervisorctl start dockerd", silent = true)
                if (!result.success) {
                    fail("Failed to start Docker daemon: ${result.stderr}")
                }
            }
        } else {
            if (dockerRunning) {
                logger.debug("Docker is already running.")
                return
            }
        }

        logger.info("Starting Docker daemon.")
        Shell.execShell("start-docker.sh", silent = true)
    }

    /**
     * Executes a docker command with the given arguments.
     * Fails if the command exits with non-zero status.
     *
     * @param args Arguments to pass to docker
     * @param silent If true, suppresses output to the console
     * @return The execution result
     * @throws dev.kannich.stdlib.JobFailedException if the command fails
     */
    suspend fun exec(vararg args: String, silent: Boolean = false): ExecResult {
        if (!isEnabled())  {
            fail("Docker is not enabled. Please enable it by calling Docker.enable().")
        }
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
    suspend fun login(username: String, password: String, registry: String? = null): ExecResult {
        secret(password)
        if (!isEnabled())  {
            fail("Docker is not enabled. Please enable it by calling Docker.enable().")
        }
        logger.info("Logging into Docker registry with username '$username' and registry '${registry ?: "Docker Hub"}'")
        val registryArg = registry ?: ""
        val result = JobContext.current().withEnv(mapOf("DOCKER_PASSWORD" to password)) {
            Shell.execShell(
                "echo \"\$DOCKER_PASSWORD\" | docker login -u \"$username\" --password-stdin $registryArg".trim(),
                silent = true
            )
        }

        if (!result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("Docker login failed: $errorMessage")
        }
        logger.info("Docker login successful.")
        return result
    }
}
