package dev.kannich.core.execution

import dev.kannich.stdlib.util.ExecResult
import dev.kannich.stdlib.util.FsUtil
import dev.kannich.stdlib.util.ProcessUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.FileVisitOption
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
    fun createJobLayer(parentLayerId: String? = null): Result<String> {
        val layerId = "layer-${UUID.randomUUID().toString().replace("-", "")}"
        val layerDir = "/kannich/overlays/$layerId"
        val lowerDir = if (parentLayerId != null) {
            getLayerWorkDir(parentLayerId)
        } else {
            "/workspace"
        }

        logger.debug("Creating job layer: $layerId (from ${parentLayerId ?: "workspace"})")

        // Create layer subdirectories: upper (changes), work (overlayfs internal), merged (view)
        FsUtil.mkdir(Path.of(layerDir, "upper")).getOrElse { return Result.failure(it) }
        FsUtil.mkdir(Path.of(layerDir, "work")).getOrElse { return Result.failure(it) }
        FsUtil.mkdir(Path.of(layerDir, "merged")).getOrElse { return Result.failure(it) }

        // Mount fuse-overlayfs
        val mountResult = ProcessUtil.execShell(
            command = "fuse-overlayfs -o lowerdir=$lowerDir,upperdir=$layerDir/upper,workdir=$layerDir/work $layerDir/merged",
            silent = true
        )

        if (!mountResult.fold({it.success}, {false})) {
            FsUtil.delete(Path.of(layerDir)).onFailure { e ->
                logger.warn("Failed to delete layer directory $layerDir after failed mount: ${e.message}")
            }
            return Result.failure(Exception("Failed to mount layer $layerId"))
        }

        logger.debug("Created job layer: $layerId")
        return Result.success(layerId)
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
     * Finds modifications and deletions inside a job layer and fills the given sets of modified and deleted file paths,
     * relative to the layer root. If a directory was deleted, only the directory will be added to the deletedPaths,
     * not all of its contents. Sets are automatically cleared before the operation.
     */
    fun findLayerModifications(layerId: String, modifiedFiles: MutableSet<Path>, deletedPaths: MutableSet<Path>) : Result<Unit> = runCatching {
        // just in case, clear the sets first
        modifiedFiles.clear()
        deletedPaths.clear()
        // the upper layer directory contains the changes made by the job
        val upperDir = Path.of(getLayerUpperDir(layerId))
        findModified(upperDir, modifiedFiles)
        findDeleted(upperDir, deletedPaths)
    }

    /**
     * Returns absolute paths of every modified file inside the upper layer directory.
     */
    private fun findModified(upperDir: Path, results: MutableSet<Path>) {
        Files.walkFileTree(upperDir, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (isOpaqueDir(dir)) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!isWhiteout(file)) {
                    results.add(file.relativize(upperDir))
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    /**
     * Returns absolute paths of every deleted file/directory inside the upper layer directory.
     * See: https://docs.kernel.org/filesystems/overlayfs.html#whiteouts-and-opaque-directories
     */
    private fun findDeleted(upperDir: Path, results: MutableSet<Path>) {
        Files.walkFileTree(upperDir, object : SimpleFileVisitor<Path>() {

            override fun preVisitDirectory(
                dir: Path,
                attrs: BasicFileAttributes
            ): FileVisitResult {
                if (isOpaqueDir(dir)) {
                    results.add(dir.relativize(upperDir))
                    // if the directory was deleted, we don't need to visit its children
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(
                file: Path,
                attrs: BasicFileAttributes
            ): FileVisitResult {
                if (isWhiteout(file)) {
                    results.add(file.relativize(upperDir))
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
    fun removeJobLayer(layerId: String) :Result<Unit>  {
        val layerDir = "/kannich/overlays/$layerId"

        // Unmount overlayfs first (fusermount -u for fuse-overlayfs)
        // Use -z (lazy) to detach immediately even if busy - cleanup happens when no longer in use
        val unmountResult = ProcessUtil.execShell("fusermount -uz $layerDir/merged", silent = true).getOrElse { return Result.failure(it) }

        if (!unmountResult.success) {
            logger.warn("Failed to unmount layer $layerId: ${unmountResult.stderr}")
        }

        // Remove layer directory
        ProcessUtil.execShell("rm -rf $layerDir", silent = true).getOrElse { return Result.failure(it) }
        logger.debug("Removed job layer: $layerId")
        return Result.success(Unit)
    }

}