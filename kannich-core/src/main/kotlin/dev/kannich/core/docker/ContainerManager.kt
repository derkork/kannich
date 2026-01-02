package dev.kannich.core.docker

import dev.kannich.core.ShutdownManager
import org.slf4j.LoggerFactory
import dev.kannich.stdlib.context.ExecResult
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.UUID
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
     * Writes content from an input stream to a file in the container.
     * Uses Docker's copy API for reliable streaming.
     *
     * @param path The absolute path to write to
     * @param content The input stream to read content from
     * @param append If true, appends to existing file instead of overwriting
     */
    fun writeFile(path: String, content: InputStream, append: Boolean = false) {
        val containerId = checkInitialized()
        client.copyToContainer(containerId, path, content, append)
    }


    /**
     * Copies multiple artifacts from the container to the host in a single tar operation.
     * This is more efficient than copying files one by one.
     *
     * Creates a staging directory with hard links to the artifacts, copies it using
     * Docker's copy API (which creates a tar), and extracts directly to the host.
     *
     * @param workDir The working directory in the container (base for relative paths)
     * @param relativePaths List of paths relative to workDir to include in the archive
     * @param hostDir The destination directory on the host
     */
    fun copyArtifacts(workDir: String, relativePaths: List<String>, hostDir: File) {
        if (relativePaths.isEmpty()) {
            return
        }

        val containerId = checkInitialized()
        logger.info("Copying ${relativePaths.size} artifacts from $workDir to ${hostDir.absolutePath}")

        // Create a staging directory with the artifact structure using hard links
        // This avoids copying file contents twice
        val stagingDir = "/tmp/artifacts-${System.currentTimeMillis()}"
        val fileListContent = relativePaths.joinToString("\n")

        // Create staging directory and link all artifacts into it
        val setupResult = execShell(
            "mkdir -p $stagingDir && cd $workDir && " +
            """while IFS= read -r f; do if [ -n "${'$'}f" ]; then d=${'$'}(dirname "${'$'}f"); mkdir -p "$stagingDir/${'$'}d"; if [ -f "${'$'}f" ]; then ln "${'$'}f" "$stagingDir/${'$'}f" 2>/dev/null || cp "${'$'}f" "$stagingDir/${'$'}f"; elif [ -d "${'$'}f" ]; then mkdir -p "$stagingDir/${'$'}f"; fi; fi; done << 'ARTIFACT_EOF'
$fileListContent
ARTIFACT_EOF""",
            silent = true
        )

        if (!setupResult.success) {
            logger.warn("Failed to stage artifacts: ${setupResult.stderr}")
            execShell("rm -rf $stagingDir", silent = true)
            return
        }

        try {
            // Copy the staging directory - Docker will tar it and we extract to host
            // The staging dir contents will be extracted directly into hostDir
            client.copyFromContainer(containerId, "$stagingDir/.", hostDir)
            logger.info("Artifacts copied successfully")
        } finally {
            // Clean up the staging directory
            execShell("rm -rf $stagingDir", silent = true)
        }
    }

    /**
     * Creates a job layer for isolation.
     * Uses fuse-overlayfs for copy-on-write semantics - instant creation, files only
     * copied when modified.
     *
     * @param parentLayerId Optional parent layer to base on. If null, uses /workspace.
     */
    fun createJobLayer(parentLayerId: String? = null): String {
        checkInitialized()

        val layerId = "layer-${UUID.randomUUID().toString().replace("-", "")}"
        val layerDir = "/kannich/overlays/$layerId"
        val lowerDir = if (parentLayerId != null) {
            getLayerWorkDir(parentLayerId)
        } else {
            "/workspace"
        }

        logger.info("Creating job layer: $layerId (from ${parentLayerId ?: "workspace"})")

        // Create layer subdirectories: upper (changes), work (overlayfs internal), merged (view)
        val mkdirResult = execShell(
            command = "mkdir -p $layerDir/upper $layerDir/work $layerDir/merged",
            silent = true
        )
        if (!mkdirResult.success) {
            throw IllegalStateException("Failed to create layer directories: ${mkdirResult.stderr}")
        }

        // Mount fuse-overlayfs
        val mountResult = execShell(
            command = "fuse-overlayfs -o lowerdir=$lowerDir,upperdir=$layerDir/upper,workdir=$layerDir/work $layerDir/merged",
            silent = true
        )
        if (!mountResult.success) {
            execShell("rm -rf $layerDir", silent = true)
            throw IllegalStateException("Failed to mount overlayfs: ${mountResult.stderr}")
        }

        logger.debug("Created job layer: $layerId")
        return layerId
    }

    /**
     * Gets the working directory for a job layer.
     * Returns the merged overlayfs mount point.
     */
    fun getLayerWorkDir(layerId: String): String {
        return "/kannich/overlays/$layerId/merged"
    }

    /**
     * Removes a job layer.
     * Unmounts the overlayfs before removing directories.
     */
    fun removeJobLayer(layerId: String) {
        // Skip cleanup if we're shutting down or already closed
        if (isClosed.get() || ShutdownManager.isShuttingDown()) {
            return
        }
        checkInitialized()

        val layerDir = "/kannich/overlays/$layerId"
        // Unmount overlayfs first (fusermount -u for fuse-overlayfs)
        execShell("fusermount -u $layerDir/merged 2>/dev/null || true", silent = true)
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
