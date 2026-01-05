package dev.kannich.core.execution

import dev.kannich.stdlib.util.ProcessUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Manages job layers for isolation.
 */
object LayerManager {

    private val logger: Logger = LoggerFactory.getLogger(LayerManager::class.java)

    /**
     * Creates a job layer for isolation. Uses fuse-overlayfs to avoid having to copy all workspace files into the layer.
     *
     * @param parentLayerId Optional parent layer to base on. If null, uses /workspace.
     */
    fun createJobLayer(parentLayerId: String? = null): String {
        val layerId = "layer-${UUID.randomUUID().toString().replace("-", "")}"
        val layerDir = "/kannich/overlays/$layerId"
        val lowerDir = if (parentLayerId != null) {
            getLayerWorkDir(parentLayerId)
        } else {
            "/workspace"
        }

        logger.debug("Creating job layer: $layerId (from ${parentLayerId ?: "workspace"})")

        // Create layer subdirectories: upper (changes), work (overlayfs internal), merged (view)
        val mkdirResult = ProcessUtil.execShell(
            command = "mkdir -p $layerDir/upper $layerDir/work $layerDir/merged",
            silent = true
        )
        if (!mkdirResult.success) {
            throw IllegalStateException("Failed to create layer directories: ${mkdirResult.stderr}")
        }

        // Mount fuse-overlayfs
        val mountResult = ProcessUtil.execShell(
            command = "fuse-overlayfs -o lowerdir=$lowerDir,upperdir=$layerDir/upper,workdir=$layerDir/work $layerDir/merged",
            silent = true
        )

        if (!mountResult.success) {
            ProcessUtil.execShell("rm -rf $layerDir", silent = true)
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
        val layerDir = "/kannich/overlays/$layerId"

        // Unmount overlayfs first (fusermount -u for fuse-overlayfs)
        // Use -z (lazy) to detach immediately even if busy - cleanup happens when no longer in use
        val unmountResult = ProcessUtil.execShell("fusermount -uz $layerDir/merged", silent = true)
        if (!unmountResult.success) {
            logger.warn("Failed to unmount layer $layerId: ${unmountResult.stderr}")
        }

        // Remove layer directory
        ProcessUtil.execShell("rm -rf $layerDir", silent = true)
        logger.debug("Removed job layer: $layerId")
    }

}