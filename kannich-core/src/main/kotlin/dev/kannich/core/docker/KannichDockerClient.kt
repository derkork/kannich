package dev.kannich.core.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Capability
import com.github.dockerjava.api.model.Device
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import org.slf4j.LoggerFactory
import dev.kannich.stdlib.context.ExecResult
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.time.Duration

/**
 * Wrapper around docker-java client providing Kannich-specific operations.
 * Uses Docker API 1.44+ (Docker 24+).
 */
class KannichDockerClient(
    private val builderImage: String = DEFAULT_BUILDER_IMAGE
) : Closeable {

    private val logger = LoggerFactory.getLogger(KannichDockerClient::class.java)
    private val dockerClient: DockerClient

    init {
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withApiVersion("1.44")
            .build()

        val httpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(config.dockerHost)
            .sslConfig(config.sslConfig)
            .maxConnections(100)
            .connectionTimeout(Duration.ofSeconds(30))
            .responseTimeout(Duration.ofSeconds(45))
            .build()

        dockerClient = DockerClientImpl.getInstance(config, httpClient)
    }

    /**
     * Checks if Docker daemon is available.
     */
    fun ping(): Boolean {
        return try {
            dockerClient.pingCmd().exec()
            true
        } catch (e: Exception) {
            logger.error("Docker daemon not available: ${e.message}")
            false
        }
    }

    /**
     * Gets the Docker version info.
     */
    fun version(): String {
        val info = dockerClient.versionCmd().exec()
        return "Docker ${info.version} (API ${info.apiVersion})"
    }

    /**
     * Pulls the builder image if not already present.
     */
    fun ensureBuilderImage() {
        val images = dockerClient.listImagesCmd()
            .withImageNameFilter(builderImage)
            .exec()

        if (images.isEmpty()) {
            logger.info("Pulling builder image: $builderImage")
            dockerClient.pullImageCmd(builderImage)
                .start()
                .awaitCompletion()
            logger.info("Builder image pulled successfully")
        }
    }

    /**
     * Creates a new build container with the project directory mounted.
     */
    fun createBuildContainer(
        projectDir: File,
        cacheDir: File,
        workingDir: String = "/workspace",
        hostProjectPath: String? = null,
        hostCachePath: String? = null
    ): String {
        // Mount project directory and cache
        val projectVolume = Volume(workingDir)
        val cacheVolume = Volume("/kannich/cache")

        // Use provided host paths or convert from File paths
        val projectPath = hostProjectPath ?: convertToDockerPath(projectDir.absolutePath)
        val cachePath = hostCachePath ?: convertToDockerPath(cacheDir.absolutePath)

        logger.debug("Mounting project: $projectPath -> $workingDir")
        logger.debug("Mounting cache: $cachePath -> /kannich/cache")

        // Build list of bind mounts
        val binds = mutableListOf(
            Bind(projectPath, projectVolume),
            Bind(cachePath, cacheVolume)
        )

        // Add Docker socket mount if needed (for nested container support)
        getDockerSocketMount()?.let { socketPath ->
            logger.debug("Mounting Docker socket: $socketPath")
            binds.add(Bind(socketPath, Volume(socketPath)))
        }

        val hostConfig = HostConfig.newHostConfig()
            .withBinds(binds)
            .withAutoRemove(false) // We'll manage cleanup ourselves
            .withInit(true) // Use tini as PID 1 for proper signal handling
            .withDevices(Device.parse("/dev/fuse")) // Required for fuse-overlayfs
            .withCapAdd(Capability.SYS_ADMIN) // Required for FUSE mounts

        val container = dockerClient.createContainerCmd(builderImage)
            .withHostConfig(hostConfig)
            .withWorkingDir(workingDir)
            .withTty(true)
            .withStdinOpen(true)
            // Keep container running so we can exec into it
            .withCmd("sleep", "infinity")
            .exec()

        logger.debug("Created container: ${container.id}")
        return container.id
    }

    /**
     * Starts a container.
     */
    fun startContainer(containerId: String) {
        dockerClient.startContainerCmd(containerId).exec()
        logger.debug("Started container: $containerId")
    }

    /**
     * Executes a command in a running container.
     * Logs output in real-time via SLF4J and returns collected result.
     *
     * @param containerId Container to execute in
     * @param command Command and arguments to execute
     * @param workingDir Working directory inside container
     * @param env Environment variables
     * @param silent If true, don't log output (for internal commands)
     */
    fun execInContainer(
        containerId: String,
        command: List<String>,
        workingDir: String? = null,
        env: Map<String, String> = emptyMap(),
        silent: Boolean = false
    ): ExecResult {
        val envList = env.map { "${it.key}=${it.value}" }

        val execCreate = dockerClient.execCreateCmd(containerId)
            .withAttachStdout(true)
            .withAttachStderr(true)
            .withCmd(*command.toTypedArray())
            .withEnv(envList)

        if (workingDir != null) {
            execCreate.withWorkingDir(workingDir)
        }

        val execId = execCreate.exec().id

        // Execute and capture output (logs in real-time)
        val callback = ExecResultCallback(silent)
        dockerClient.execStartCmd(execId)
            .exec(callback)
            .awaitCompletion()

        // Get exit code
        val inspectResult = dockerClient.inspectExecCmd(execId).exec()
        val exitCode = inspectResult.exitCodeLong?.toInt() ?: -1

        return ExecResult(
            stdout = callback.stdout,
            stderr = callback.stderr,
            exitCode = exitCode
        )
    }

    /**
     * Copies content from an input stream to a file in the container.
     *
     * Uses a two-step process: first copies to /tmp via Docker API, then moves
     * to the target path using exec. This ensures the file is visible to internal
     * mounts like overlayfs (Docker's copy API writes to the root filesystem,
     * not to mounts created inside the container).
     *
     * @param containerId Container to copy to
     * @param path The absolute path to write to in the container
     * @param content The input stream to read content from
     * @param append If true, appends to existing file instead of overwriting
     */
    fun copyToContainer(containerId: String, path: String, content: InputStream, append: Boolean = false) {
        logger.debug("copyToContainer: path=$path, append=$append")

        // Generate a unique temp file name
        val tempFile = "/tmp/kannich-write-${System.currentTimeMillis()}-${System.nanoTime()}"

        if (append) {
            // For append, we need to read existing content first, concatenate, then write
            val existingContent = try {
                val result = execInContainer(containerId, listOf("cat", path), silent = true)
                if (result.success) result.stdout.toByteArray() else ByteArray(0)
            } catch (e: Exception) {
                ByteArray(0)
            }
            val newContent = content.readBytes()
            val combined = existingContent + newContent
            copyToTempFile(containerId, tempFile, ByteArrayInputStream(combined))
        } else {
            copyToTempFile(containerId, tempFile, content)
        }

        // Move from temp to target path (this runs inside container, sees overlay mounts)
        val moveResult = execInContainer(containerId, listOf("mv", tempFile, path), silent = true)
        if (!moveResult.success) {
            // Clean up temp file on failure
            execInContainer(containerId, listOf("rm", "-f", tempFile), silent = true)
            throw IllegalStateException("Failed to move file to $path: ${moveResult.stderr}")
        }
        logger.debug("copyToContainer: file written to $path")
    }

    private fun copyToTempFile(containerId: String, tempFile: String, content: InputStream) {
        // Read content into byte array (needed to set tar entry size)
        val contentBytes = content.readBytes()
        logger.debug("copyToTempFile: writing ${contentBytes.size} bytes to $tempFile")

        val fileName = tempFile.substringAfterLast('/')

        // Create tar archive in memory
        val tarBytes = ByteArrayOutputStream()
        TarArchiveOutputStream(tarBytes).use { tar ->
            val entry = TarArchiveEntry(fileName)
            entry.size = contentBytes.size.toLong()
            tar.putArchiveEntry(entry)
            tar.write(contentBytes)
            tar.closeArchiveEntry()
        }

        // Copy tar to container's /tmp
        dockerClient.copyArchiveToContainerCmd(containerId)
            .withRemotePath("/tmp")
            .withTarInputStream(ByteArrayInputStream(tarBytes.toByteArray()))
            .exec()
    }

    /**
     * Copies files from container to host.
     */
    fun copyFromContainer(containerId: String, containerPath: String, hostPath: File) {
        logger.info("Copying $containerPath from container to $hostPath")

        // Verify path exists first - if it doesn't, Docker returns an error response
        // instead of tar data, which causes TarArchiveInputStream to block indefinitely
        val checkResult = execInContainer(
            containerId,
            listOf("test", "-e", containerPath),
            silent = true
        )
        if (!checkResult.success) {
            throw IllegalStateException("Path does not exist in container: $containerPath")
        }

        dockerClient.copyArchiveFromContainerCmd(containerId, containerPath)
            .exec()
            .use { tarStream ->
                TarExtractor.extract(tarStream, hostPath)
            }

        logger.info("Extraction complete")
    }

    /**
     * Stops a container.
     */
    fun stopContainer(containerId: String) {
        try {
            dockerClient.stopContainerCmd(containerId)
                .withTimeout(10)
                .exec()
            logger.debug("Stopped container: $containerId")
        } catch (e: Exception) {
            logger.warn("Failed to stop container $containerId: ${e.message}")
        }
    }

    /**
     * Removes a container.
     */
    fun removeContainer(containerId: String) {
        try {
            dockerClient.removeContainerCmd(containerId)
                .withForce(true)
                .exec()
            logger.debug("Removed container: $containerId")
        } catch (e: Exception) {
            logger.warn("Failed to remove container $containerId: ${e.message}")
        }
    }

    override fun close() {
        dockerClient.close()
    }

    /**
     * Determines the Docker socket path to mount for nested container support.
     * Returns null if using TCP (no mount needed) or the socket path to mount.
     *
     * Logic mirrors kannichw wrapper script:
     * - DOCKER_HOST=unix:///path -> mount that path
     * - DOCKER_HOST=tcp://... -> no mount needed (env var handles it)
     * - No DOCKER_HOST -> mount default /var/run/docker.sock
     */
    private fun getDockerSocketMount(): String? {
        val dockerHost = System.getenv("DOCKER_HOST")

        return when {
            dockerHost == null || dockerHost.isBlank() -> {
                // No DOCKER_HOST set, use default socket
                "/var/run/docker.sock"
            }
            dockerHost.startsWith("unix://") -> {
                // Extract socket path from unix:///path/to/socket
                dockerHost.removePrefix("unix://")
            }
            dockerHost.startsWith("tcp://") -> {
                // TCP connection - no socket mount needed
                null
            }
            else -> {
                // Unknown format, try default socket
                logger.warn("Unknown DOCKER_HOST format: $dockerHost, using default socket")
                "/var/run/docker.sock"
            }
        }
    }

    /**
     * Converts a Windows path to Docker-compatible format.
     * E.g., "D:\foo\bar" -> "/d/foo/bar" for Docker Desktop on Windows.
     */
    private fun convertToDockerPath(path: String): String {
        // Check if this is a Windows path (e.g., D:\foo\bar)
        if (path.length >= 2 && path[1] == ':') {
            val driveLetter = path[0].lowercaseChar()
            val remainder = path.substring(2).replace('\\', '/')
            return "/$driveLetter$remainder"
        }
        // Already Unix-style or relative
        return path.replace('\\', '/')
    }

    companion object {
        const val DEFAULT_BUILDER_IMAGE = "derkork/kannich:latest"
    }
}
