package dev.kannich.stdlib.util

import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 * Utility for filesystem operations. All operations work on absolute paths.
 */
object FsUtil {
    private val logger = LoggerFactory.getLogger(FsUtil::class.java)

    /**
     * Verifies that the path is absolute.
     * @throws IllegalArgumentException if the path is not absolute
     */
    private fun checkAbsolute(path: Path) {
        if (!path.isAbsolute) {
            throw IllegalArgumentException("Path must be absolute: $path")
        }
    }

    /**
     * Creates a temporary directory and returns its path.
     */
    fun mktemp(prefix: String = "kannich"): Result<Path> = runCatching {
        logger.debug("Creating temporary directory with prefix: {}", prefix)
        Files.createTempDirectory(prefix).toAbsolutePath()
    }

    /**
     * Creates a directory and all parent directories if they don't exist.
     */
    fun mkdir(path: Path): Result<Unit> = runCatching {
        logger.debug("Creating directory: {}", path)
        checkAbsolute(path)
        Files.createDirectories(path)
    }

    /**
     * Copies the source file or directory to the destination. Automatically creates missing directories of the destination.
     */
    fun copy(src: Path, dest: Path): Result<Unit> = runCatching {
        logger.debug("Copying {} to {}", src, dest)
        checkAbsolute(src)
        checkAbsolute(dest)

        if (Files.isDirectory(src)) {
            Files.walkFileTree(src, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val targetDir = dest.resolve(src.relativize(dir))
                    Files.createDirectories(targetDir)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val targetFile = dest.resolve(src.relativize(file))
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
                    return FileVisitResult.CONTINUE
                }
            })
        } else {
            dest.parent?.let { Files.createDirectories(it) }
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Moves file or directory to a destination. Automatically creates missing directories of the destination.
     */
    fun move(src: Path, dest: Path): Result<Unit> = runCatching {
        logger.debug("Moving {} to {}", src, dest)
        checkAbsolute(src)
        checkAbsolute(dest)

        dest.parent?.let { Files.createDirectories(it) }
        Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * Deletes file or directory (recursively if it's a directory). If the path doesn't exist, does nothing.
     */
    fun delete(path: Path): Result<Unit> = runCatching {
        logger.debug("Deleting path: {}", path)
        checkAbsolute(path)
        if (!Files.exists(path)) return@runCatching

        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    /**
     * Checks if a path exists.
     */
    fun exists(path: Path): Result<Boolean> = runCatching {
        logger.debug("Checking if path exists: {}", path)
        checkAbsolute(path)
        Files.exists(path)
    }

    /**
     * Checks if a path is a directory.
     */
    fun isDirectory(path: Path): Result<Boolean> = runCatching {
        logger.debug("Checking if directory: {}", path)
        checkAbsolute(path)
        Files.isDirectory(path)
    }

    /**
     * Checks if a path is a file.
     */
    fun isFile(path: Path): Result<Boolean> = runCatching {
        logger.debug("Checking if file: {}", path)
        checkAbsolute(path)
        Files.isRegularFile(path)
    }

    /**
     * Writes text content to a file.
     */
    fun write(path: Path, content: String, append: Boolean = false): Result<Unit> {
        return write(path, content.toByteArray().inputStream(), append)
    }

    /**
     * Writes binary content from an input stream to a file.
     */
    fun write(path: Path, content: InputStream, append: Boolean = false): Result<Unit> = runCatching {
        logger.debug("Writing input stream to {}{}", path, if (append) " (append)" else "")
        checkAbsolute(path)
        path.parent?.let { Files.createDirectories(it) }
        if (append) {
            Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND).use { output ->
                content.copyTo(output)
            }
        } else {
            Files.copy(content, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Finds files matching ant-style glob patterns.
     */
    fun glob(
        includes: List<String>,
        excludes: List<String> = emptyList(),
        rootPath: Path
    ): Result<List<String>> = runCatching {
        logger.debug(
            "Finding files with glob: includes={}, excludes={}, rootPath={}",
            includes,
            excludes,
            rootPath
        )
        checkAbsolute(rootPath)

        val matchingPaths = mutableSetOf<String>()

        // Separate literal paths from patterns with wildcards
        val (literalPaths, patterns) = includes.partition { AntPathMatcher.isLiteralPath(it) }

        // For literal paths, just check if they exist (no traversal needed)
        for (path in literalPaths) {
            val file = rootPath.resolve(path)
            if (Files.exists(file)) {
                val matchesExclude = excludes.isNotEmpty() &&
                        AntPathMatcher.matchesAny(excludes, path)
                if (!matchesExclude) {
                    matchingPaths.add(path)
                }
            }
        }

        // For patterns with wildcards, use recursive traversal with optimizations
        if (patterns.isNotEmpty()) {
            val basePaths = AntPathMatcher.getBasePaths(patterns)

            // Limit depth if no ** pattern is present to avoid unnecessary traversal
            val maxPatternDepth = patterns.maxOfOrNull {
                if (it.contains("**")) Int.MAX_VALUE else it.split("/").count { part -> part.isNotEmpty() }
            } ?: Int.MAX_VALUE

            val searchPaths = if (basePaths.isEmpty() || basePaths.contains("")) {
                listOf(rootPath)
            } else {
                basePaths.map { rootPath.resolve(it) }
            }

            for (searchPath in searchPaths) {
                if (!Files.exists(searchPath)) continue

                // Calculate depth using NIO path components to handle OS-specific separators
                val relativizedBase = rootPath.relativize(searchPath)
                val baseDepth = if (relativizedBase.toString().isEmpty()) 0 else relativizedBase.nameCount

                val walkMaxDepth = if (maxPatternDepth == Int.MAX_VALUE) {
                    Int.MAX_VALUE
                } else {
                    maxOf(0, maxPatternDepth - baseDepth)
                }

                Files.walk(searchPath, walkMaxDepth).use { stream ->
                    stream.forEach { file ->
                        if (Files.isSameFile(file, rootPath)) return@forEach

                        try {
                            val relativePath = rootPath.relativize(file).toUnixString()
                            if (relativePath.isEmpty()) return@forEach

                            val matchesInclude = AntPathMatcher.matchesAny(patterns, relativePath)
                            val matchesExclude = excludes.isNotEmpty() &&
                                    AntPathMatcher.matchesAny(excludes, relativePath)

                            if (matchesInclude && !matchesExclude) {
                                matchingPaths.add(relativePath)
                            }
                        } catch (_: Exception) {
                            // Skip files that can't be made relative
                        }
                    }
                }
            }
        }

        matchingPaths.toList().sorted()
    }

    /**
     * Finds files matching a single ant-style glob pattern.
     */
    fun glob(pattern: String, rootPath: Path): Result<List<String>> {
        return glob(listOf(pattern), emptyList(), rootPath)
    }
}

/**
 * Normalizes the path separators to forward slashes.
 */
fun Path.toUnixString() = this.toString().replace('\\', '/')
