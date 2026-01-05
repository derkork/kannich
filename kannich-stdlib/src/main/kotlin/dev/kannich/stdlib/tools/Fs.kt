package dev.kannich.stdlib.tools

import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.fail
import dev.kannich.stdlib.util.AntPathMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 * Built-in tool for filesystem operations.
 * Provides a clean API for common file/directory operations.
 *
 * Some operations support shell glob patterns (*, ?, [...]) in source paths.
 * Whitespace and shell metacharacters are automatically escaped while preserving globs.
 */
object Fs {
    private val logger = LoggerFactory.getLogger(Fs::class.java)

    /**
     * Resolves a path against the current working directory.
     * - if the path is null or empty, the function returns the working directory from JobContext.current().
     * - If the path is absolute, returns the path as is
     * - otherwise treats the path as subpath of the working directory and returns the full path.
     *
     * @param path The path to resolve. If null or empty, returns the working directory.
     * @return The resolved absolute path.
     */
    suspend fun resolve(path: String? = null): String {
        logger.debug("Resolving path: {}", path ?: "working directory")
        val ctx = JobContext.current()
        return Path.of(ctx.workingDir).resolve(path ?: "").toUnixString()
    }

    /**
     * Same as [resolve] but returns a [Path] object.
     */
    private suspend fun resolvePath(path: String): Path {
        return Path.of(resolve(path))
    }

    /**
     * Creates a temporary directory and returns its path.
     *
     * @param prefix Optional prefix for the temp directory name
     * @return The absolute path to the created temporary directory.
     * @throws dev.kannich.stdlib.JobFailedException if creation fails
     */
    suspend fun mktemp(prefix: String = "kannich"): String {
        logger.debug("Creating temporary directory with prefix: {}", prefix)
        try {
            return withContext(Dispatchers.IO) { Files.createTempDirectory(prefix) }.toAbsolutePath().toUnixString()
        } catch (e: Exception) {
            fail("Failed to create temporary directory with prefix $prefix: ${e.message}")
        }
    }

    /**
     * Creates a directory and all parent directories if they don't exist.
     *
     * @param path The directory path to create
     * @throws dev.kannich.stdlib.JobFailedException if creation fails
     */
    suspend fun mkdir(path: String) {
        logger.debug("Creating directory: {}", path)
        try {
            withContext(Dispatchers.IO) { Files.createDirectories(resolvePath(path)) }
        } catch (e: Exception) {
            fail("Failed to create directory $path: ${e.message}")
        }
    }

    /**
     * Copies the source file or directory to the destination. Automatically creates missing directories of the destination.
     *
     * @param src The source path.
     * @param dest The destination path.
     * @throws dev.kannich.stdlib.JobFailedException if copy fails
     */
    suspend fun copy(src: String, dest: String) {
        logger.debug("Copying {} to {}", src, dest)
        val source = resolvePath(src)
        val target = resolvePath(dest)

        try {
            if (Files.isDirectory(source)) {
                withContext(Dispatchers.IO) {
                    Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
                        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                            val targetDir = target.resolve(source.relativize(dir))
                            Files.createDirectories(targetDir)
                            return FileVisitResult.CONTINUE
                        }

                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            val targetFile = target.resolve(source.relativize(file))
                            Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
                            return FileVisitResult.CONTINUE
                        }
                    })
                }
            } else {
                target.parent?.let { Files.createDirectories(it) }
                withContext(Dispatchers.IO) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } catch (e: Exception) {
            fail("Failed to copy $src to $target: ${e.message}")
        }
    }

    /**
     * Moves file or directory to a destination. Automatically creates missing directories of the destination.
     *
     * @param src The source path.
     * @param dest The destination path.
     * @throws dev.kannich.stdlib.JobFailedException if move fails
     */
    suspend fun move(src: String, dest: String) {
        logger.debug("Moving {} to {}", src, dest)
        val source = resolvePath(src)
        val target = resolvePath(dest)

        try {
            target.parent?.let { Files.createDirectories(it) }
            withContext(Dispatchers.IO) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            fail("Failed to move $src to $target: ${e.message}")
        }
    }

    /**
     * Deletes file or directory (recursively if it's a directory). If the path doesn't exist, does nothing.
     *
     * @param path The path to delete.
     * @throws dev.kannich.stdlib.JobFailedException if deletion fails
     */
    suspend fun delete(path: String) {
        logger.debug("Deleting path: {}", path)
        val target = resolvePath(path)
        if (!Files.exists(target)) return

        try {
            withContext(Dispatchers.IO) {
                Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
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
        } catch (e: Exception) {
            fail("Failed to delete $path: ${e.message}")
        }
    }

    /**
     * Checks if a path exists.
     *
     * @param path The path to check.
     * @return true if the path exists
     */
    suspend fun exists(path: String): Boolean {
        logger.debug("Checking if path exists: {}", path)
        return Files.exists(resolvePath(path))
    }

    /**
     * Checks if a path is a directory.
     *
     * @param path The path to check.
     * @return true if the path is a directory
     */
    suspend fun isDirectory(path: String): Boolean {
        logger.debug("Checking if directory: {}", path)
        return Files.isDirectory(resolvePath(path))
    }

    /**
     * Checks if a path is a file.
     *
     * @param path The path to check.
     * @return true if the path is a file
     */
    suspend fun isFile(path: String): Boolean {
        logger.debug("Checking if file: {}", path)
        return Files.isRegularFile(resolvePath(path))
    }

    /**
     * Writes text content to a file.
     * Creates parent directories if needed. Overwrites an existing file (unless append is true).
     *
     * @param path The file path to write to
     * @param content The text content to write
     * @param append If true, appends content to the file instead of overwriting
     * @throws dev.kannich.stdlib.JobFailedException if write fails
     */
    suspend fun write(path: String, content: String, append: Boolean = false) {
        logger.debug("Writing string content to {}{}", path, if (append) " (append)" else "")
        write(path, ByteArrayInputStream(content.toByteArray()), append)
    }

    /**
     * Writes binary content from an input stream to a file.
     * Creates parent directories if needed. Overwrites an existing file (unless append is true).
     *
     * @param path The file path to write to.
     * @param content The input stream to read content from
     * @param append If true, appends content to the file instead of overwriting
     * @throws dev.kannich.stdlib.JobFailedException if write fails
     */
    suspend fun write(path: String, content: InputStream, append: Boolean = false) {
        logger.debug("Writing input stream to {}{}", path, if (append) " (append)" else "")
        val target = resolvePath(path)
        try {
            target.parent?.let { Files.createDirectories(it) }
            if (append) {
                withContext(Dispatchers.IO) {
                    Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.APPEND).use { output ->
                        content.copyTo(output)
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } catch (e: Exception) {
            fail("Failed to write to $path: ${e.message}")
        }
    }

    /**
     * Finds files matching ant-style glob patterns.
     *
     * Pattern syntax:
     * - [*] matches 0 or more characters except [/]
     * - [**] matches 0 or more characters including [/]
     * - [?] matches exactly one character
     *
     * Examples: target/&#42;&#42;/&#42;.jar, src/main/resources/&#42;.xml, foo.txt
     *
     * @param includes Patterns for files to include
     * @param excludes Patterns for files to exclude (default: empty)
     * @param baseDir Base directory to search from (default: current working directory)
     * @return List of relative paths matching the patterns
     */
    suspend fun glob(
        includes: List<String>,
        excludes: List<String> = emptyList(),
        baseDir: String? = null
    ): List<String> {
        logger.debug(
            "Finding files with glob: includes={}, excludes={}, baseDir={}",
            includes,
            excludes,
            baseDir ?: "cwd"
        )
        val rootPath = resolvePath(baseDir ?: "")

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

                // Calculate depth using NIO path components to handle OS-specific separators (e.g. for local development)
                val relativizedBase = rootPath.relativize(searchPath)
                val baseDepth = if (relativizedBase.toString().isEmpty()) 0 else relativizedBase.nameCount

                val walkMaxDepth = if (maxPatternDepth == Int.MAX_VALUE) {
                    Int.MAX_VALUE
                } else {
                    maxOf(0, maxPatternDepth - baseDepth)
                }

                try {
                    withContext(Dispatchers.IO) {
                        Files.walk(searchPath, walkMaxDepth).use { stream ->
                            stream.forEach { file ->
                                @Suppress("BlockingMethodInNonBlockingContext")
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
                } catch (_: Exception) {
                    // Skip search directory if walking fails
                }
            }
        }

        return matchingPaths.toList().sorted()
    }

    /**
     * Finds files matching a single ant-style glob pattern.
     *
     * @param pattern The pattern to match
     * @param baseDir Base directory to search from (default: current working directory)
     * @return List of relative paths matching the pattern
     */
    suspend fun glob(pattern: String, baseDir: String? = null): List<String> {
        logger.debug("Finding files with glob: pattern={}, baseDir={}", pattern, baseDir ?: "cwd")
        return glob(listOf(pattern), emptyList(), baseDir)
    }

}

/**
 * Normalizes the path separators to forward slashes.
 */
fun Path.toUnixString() = this.toString().replace('\\', '/')

