package dev.kannich.core.docker

import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File

/**
 * Manages the lifecycle of a Kannich build container.
 * Handles container creation, job execution, and cleanup.
 */
class ContainerManager(
    private val client: KannichDockerClient,
    private val projectDir: File,
    private val cacheDir: File,
    private val hostProjectPath: String? = null,
    private val hostCachePath: String? = null
) : Closeable {

    private val logger = LoggerFactory.getLogger(ContainerManager::class.java)
    private var containerId: String? = null
    private var isStarted = false

    /**
     * The project directory path inside the container.
     */
    val containerProjectDir: String = "/workspace"

    /**
     * The cache directory path inside the container.
     */
    val containerCacheDir: String = "/kannich/cache"

    /**
     * Initializes the build container.
     * Pulls the builder image if needed and creates the container.
     */
    fun initialize() {
        logger.info("Initializing build container...")
        client.ensureBuilderImage()
        containerId = client.createBuildContainer(projectDir, cacheDir, "/workspace", hostProjectPath, hostCachePath)
        client.startContainer(containerId!!)
        isStarted = true
        logger.info("Build container ready: ${containerId?.take(12)}")

        // Verify mounts are accessible
        verifyMounts()
    }

    /**
     * Verifies that required volume mounts are accessible.
     * Throws an exception if mounts are not working correctly.
     */
    private fun verifyMounts() {
        // Verify project directory is mounted and accessible
        val projectCheck = exec(listOf("test", "-d", containerProjectDir), silent = true)
        if (!projectCheck.success) {
            throw IllegalStateException(
                "Project directory mount failed: $containerProjectDir is not accessible in container. " +
                "Host path: ${hostProjectPath ?: projectDir.absolutePath}"
            )
        }

        // Verify cache directory is mounted and writable
        val cacheCheck = exec(listOf("test", "-d", containerCacheDir), silent = true)
        if (!cacheCheck.success) {
            throw IllegalStateException(
                "Cache directory mount failed: $containerCacheDir is not accessible in container. " +
                "Host path: ${hostCachePath ?: cacheDir.absolutePath}. " +
                "Ensure the cache directory exists on the host before starting."
            )
        }

        // Verify cache is writable by creating a test file
        val writeCheck = execShell(
            "touch $containerCacheDir/.kannich-mount-test && rm $containerCacheDir/.kannich-mount-test",
            silent = true
        )
        if (!writeCheck.success) {
            throw IllegalStateException(
                "Cache directory is not writable: $containerCacheDir. " +
                "Check permissions on host path: ${hostCachePath ?: cacheDir.absolutePath}"
            )
        }

        logger.debug("Volume mounts verified successfully")
    }

    /**
     * Executes a command in the build container.
     * Logs output in real-time and returns collected result.
     *
     * @param command Command and arguments to execute
     * @param workingDir Working directory inside container
     * @param env Environment variables
     * @param silent If true, don't log output (for internal commands)
     */
    fun exec(
        command: List<String>,
        workingDir: String = "/workspace",
        env: Map<String, String> = emptyMap(),
        silent: Boolean = false
    ): ExecResult {
        checkInitialized()

        logger.debug("Executing: ${command.joinToString(" ")}")
        return client.execInContainer(
            containerId!!,
            command,
            workingDir,
            env,
            silent
        )
    }

    /**
     * Executes a shell command in the build container.
     * Wraps the command in sh -c for shell interpretation.
     */
    fun execShell(
        command: String,
        workingDir: String = "/workspace",
        env: Map<String, String> = emptyMap(),
        silent: Boolean = false
    ): ExecResult {
        return exec(listOf("sh", "-c", command), workingDir, env, silent)
    }

    /**
     * Copies artifacts from the container to the host.
     */
    fun copyArtifacts(containerPath: String, hostDir: File) {
        checkInitialized()
        logger.info("Copying artifacts from $containerPath to ${hostDir.absolutePath}")
        client.copyFromContainer(containerId!!, containerPath, hostDir)
    }

    /**
     * Creates a job layer for isolation.
     * Uses copy-on-write semantics by copying from a parent layer or workspace.
     *
     * @param parentLayerId Optional parent layer to copy from. If null, copies from /workspace.
     */
    fun createJobLayer(parentLayerId: String? = null): String {
        checkInitialized()

        val layerId = "layer-${System.currentTimeMillis()}"
        val layerDir = "/kannich/overlays/$layerId"
        val sourceDir = if (parentLayerId != null) {
            getLayerWorkDir(parentLayerId)
        } else {
            "/workspace"
        }

        // Create layer directory (silent - internal operation)
        exec(listOf("mkdir", "-p", layerDir), silent = true)
        logger.info("Creating job layer: $layerId (from ${parentLayerId ?: "workspace"})")

        // Copy source to layer (silent - internal operation)
        logger.debug("Copying $sourceDir to layer...")
        val copyResult = execShell(
            command = "cp -a $sourceDir/. $layerDir/",
            silent = true
        )

        if (!copyResult.success) {
            logger.warn("Copy command failed!")
            if (copyResult.stderr.isNotEmpty()) {
                logger.warn("stderr: ${copyResult.stderr}")
            }
        }

        logger.debug("Created job layer: $layerId")
        return layerId
    }

    /**
     * Gets the working directory for a job layer.
     */
    fun getLayerWorkDir(layerId: String): String {
        return "/kannich/overlays/$layerId"
    }

    /**
     * Removes a job layer.
     */
    fun removeJobLayer(layerId: String) {
        checkInitialized()

        val layerDir = "/kannich/overlays/$layerId"
        execShell("rm -rf $layerDir", silent = true)
        logger.debug("Removed job layer: $layerId")
    }

    private fun checkInitialized() {
        check(containerId != null && isStarted) {
            "Container not initialized. Call initialize() first."
        }
    }

    override fun close() {
        containerId?.let { id ->
            logger.info("Cleaning up build container: ${id.take(12)}")
            client.stopContainer(id)
            client.removeContainer(id)
        }
        containerId = null
        isStarted = false
    }
}
