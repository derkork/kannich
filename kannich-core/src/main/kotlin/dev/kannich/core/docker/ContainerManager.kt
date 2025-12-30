package dev.kannich.core.docker

import dev.kannich.core.ShutdownManager
import org.slf4j.LoggerFactory
import dev.kannich.stdlib.context.ExecResult
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the lifecycle of a Kannich build container.
 * Handles container creation, job execution, and cleanup.
 * Thread-safe and idempotent for proper shutdown handling.
 */
class ContainerManager(
    private val client: KannichDockerClient,
    private val projectDir: File,
    private val cacheDir: File,
    private val hostProjectPath: String? = null,
    private val hostCachePath: String? = null
) : Closeable {

    private val logger = LoggerFactory.getLogger(ContainerManager::class.java)
    private val containerIdRef = AtomicReference<String?>(null)
    private val isStarted = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)

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
     * Registers with ShutdownManager for cleanup on SIGINT/SIGTERM.
     */
    fun initialize() {
        logger.info("Initializing build container...")
        client.ensureBuilderImage()
        val id = client.createBuildContainer(projectDir, cacheDir, "/workspace", hostProjectPath, hostCachePath)
        containerIdRef.set(id)
        client.startContainer(id)
        isStarted.set(true)
        logger.info("Build container ready: ${id.take(12)}")

        // Register for cleanup on shutdown
        ShutdownManager.register(this)

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
        val containerId = checkInitialized()

        if (!silent) {
            logger.info("Executing: ${command.joinToString(" ")}")
        }
        else {
            logger.debug("Executing: ${command.joinToString(" ")}")
        }
        return client.execInContainer(
            containerId,
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
        val containerId = checkInitialized()
        logger.info("Copying artifacts from $containerPath to ${hostDir.absolutePath}")
        client.copyFromContainer(containerId, containerPath, hostDir)
    }

    /**
     * Creates a job layer for isolation.
     * Uses copy-on-write semantics by copying from a parent layer or workspace.
     *
     * @param parentLayerId Optional parent layer to copy from. If null, copies from /workspace.
     */
    fun createJobLayer(parentLayerId: String? = null): String {
        checkInitialized() // Just validate, don't need the ID here

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
        // Skip cleanup if we're shutting down or already closed
        if (isClosed.get() || ShutdownManager.isShuttingDown()) {
            return
        }
        checkInitialized()

        val layerDir = "/kannich/overlays/$layerId"
        execShell("rm -rf $layerDir", silent = true)
        logger.debug("Removed job layer: $layerId")
    }

    private fun checkInitialized(): String {
        val containerId = containerIdRef.get()
        check(containerId != null && isStarted.get()) {
            "Container not initialized. Call initialize() first."
        }
        return containerId
    }

    /**
     * Cleans up the build container.
     * Thread-safe and idempotent - safe to call multiple times or from shutdown hook.
     */
    override fun close() {
        // Ensure we only close once
        if (!isClosed.compareAndSet(false, true)) {
            return
        }

        // Unregister from shutdown manager (no-op if called from shutdown hook)
        ShutdownManager.unregister(this)

        val containerId = containerIdRef.getAndSet(null)
        if (containerId != null) {
            logger.info("Cleaning up build container: ${containerId.take(12)}")
            client.stopContainer(containerId)
            client.removeContainer(containerId)
        }
        isStarted.set(false)
    }
}
