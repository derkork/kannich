package dev.kannich.core.execution

import dev.kannich.stdlib.util.ProcessUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.UserDefinedFileAttributeView
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
     * Returns the upper layer directory for a job layer.
     */
    private fun getLayerUpperDir(layerId: String): String {
        return "/kannich/overlays/$layerId/upper"
    }

    /**
     * Finds modifications and deletions inside a job layer and returns a list of modified and deleted file paths.
     */
    fun findLayerModifications(layerId: String, modifications: MutableList<Path>, deletions: MutableList<Path>) {
        // just in case, clear the lists first
        modifications.clear()
        deletions.clear()
        // the upper layer directory contains the changes made by the job
        val upperDir = Path.of(getLayerUpperDir(layerId))
        // first find all deleted paths
        findDeleted(upperDir, deletions)
        // then do another walk and filter out deleted paths
        modifications.addAll(Files.walk(upperDir).filter { !deletions.contains(it) }.toList())
    }

    /**
     * Returns absolute paths of every deleted file/directory inside the upper layer directory.
     * See: https://docs.kernel.org/filesystems/overlayfs.html#whiteouts-and-opaque-directories
     */
    private fun findDeleted(upperDir: Path, results:MutableList<Path>) {
        Files.walkFileTree(upperDir, object : SimpleFileVisitor<Path>() {

            override fun visitFile(
                file: Path,
                attrs: BasicFileAttributes
            ): FileVisitResult {
                if (isWhiteout(file) || isOpaqueDir(file)) {
                    results.add(file.toAbsolutePath().normalize())
                }
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(
                dir: Path,
                exc: IOException?
            ): FileVisitResult {
                if (isOpaqueDir(dir)) {
                    results.add(dir.toAbsolutePath().normalize())
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun isWhiteout(p: Path): Boolean {
        /*  0-byte file with xattr */
        if (Files.isRegularFile(p) && Files.size(p) == 0L) {
            val view = Files.getFileAttributeView(
                p, UserDefinedFileAttributeView::class.java
            )
            if (view != null && "trusted.overlay.whiteout" in view.list()) {
                return true
            }
        }

        /* char device 0/0 */
        return try {
            val major = Files.getAttribute(p, "unix:major") as Int
            val minor = Files.getAttribute(p, "unix:minor") as Int
            major == 0 && minor == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun isOpaqueDir(p: Path): Boolean {
        if (!Files.isDirectory(p)) {
            return false
        }
        return try {
            val opaque = Files.getAttribute(
                p, "trusted.overlay.opaque", LinkOption.NOFOLLOW_LINKS
            ) as String?
            opaque == "y"
        } catch (_: Exception) {
            false
        }
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