package dev.kannich.tools

import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.fail
import dev.kannich.stdlib.FsUtil
import dev.kannich.stdlib.FsKind
import dev.kannich.stdlib.toUnixString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.file.Path

/**
 * Built-in tool for filesystem operations.
 * Provides a clean API for common file/directory operations.
 *
 * Some operations support shell glob patterns (*, ?, [...]) in source paths.
 * Whitespace and shell metacharacters are automatically escaped while preserving globs.
 */
object Fs {
    /**
     * Resolves the given path relative to the current working directory.
     */
    private suspend fun resolvePath(path: String): Path {
        val ctx = JobContext.current()
        return Path.of(
            Path.of(ctx.workingDir).resolve(path).toUnixString()
        )
    }

    /**
     * Creates a temporary directory and returns its path.
     *
     * @param prefix Optional prefix for the temp directory name
     * @return The absolute path to the created temporary directory.
     * @throws dev.kannich.stdlib.JobFailedException if creation fails
     */
    suspend fun mktemp(prefix: String = "kannich"): String {
        return withContext(Dispatchers.IO) {
            FsUtil.mktemp(prefix).getOrElse { e ->
                fail("Failed to create temporary directory with prefix $prefix: ${e.message}")
            }.toUnixString()
        }
    }

    /**
     * Creates a directory and all parent directories if they don't exist.
     *
     * @param path The directory path to create
     * @throws dev.kannich.stdlib.JobFailedException if creation fails
     */
    suspend fun mkdir(path: String) {
        val target = resolvePath(path)
        withContext(Dispatchers.IO) {
            FsUtil.mkdir(target).onFailure { e ->
                fail("Failed to create directory $path: ${e.message}")
            }
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
        val source = resolvePath(src)
        val target = resolvePath(dest)
        withContext(Dispatchers.IO) {
            FsUtil.copy(source, target).onFailure { e ->
                fail("Failed to copy $src to $target: ${e.message}")
            }
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
        val source = resolvePath(src)
        val target = resolvePath(dest)
        withContext(Dispatchers.IO) {
            FsUtil.move(source, target).onFailure { e ->
                fail("Failed to move $src to $target: ${e.message}")
            }
        }
    }

    /**
     * Deletes file or directory (recursively if it's a directory). If the path doesn't exist, does nothing.
     *
     * @param path The path to delete.
     * @throws dev.kannich.stdlib.JobFailedException if deletion fails
     */
    suspend fun delete(path: String) {
        val target = resolvePath(path)
        withContext(Dispatchers.IO) {
            FsUtil.delete(target).onFailure { e ->
                fail("Failed to delete $path: ${e.message}")
            }
        }
    }

    /**
     * Checks if a path exists.
     *
     * @param path The path to check.
     * @return true if the path exists
     */
    suspend fun exists(path: String): Boolean {
        val target = resolvePath(path)
        return withContext(Dispatchers.IO) {
            FsUtil.exists(target).getOrElse { e ->
                fail("Failed to check if path exists $path: ${e.message}")
            }
        }
    }

    /**
     * Checks if a path is a directory.
     *
     * @param path The path to check.
     * @return true if the path is a directory
     */
    suspend fun isDirectory(path: String): Boolean {
        val target = resolvePath(path)
        return withContext(Dispatchers.IO) {
            FsUtil.isDirectory(target).getOrElse { e ->
                fail("Failed to check if directory $path: ${e.message}")
            }
        }
    }

    /**
     * Checks if a path is a file.
     *
     * @param path The path to check.
     * @return true if the path is a file
     */
    suspend fun isFile(path: String): Boolean {
        val target = resolvePath(path)
        return withContext(Dispatchers.IO) {
            FsUtil.isFile(target).getOrElse { e ->
                fail("Failed to check if file $path: ${e.message}")
            }
        }
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
        val target = resolvePath(path)
        withContext(Dispatchers.IO) {
            FsUtil.write(target, content, append).onFailure { e ->
                fail("Failed to write to $path: ${e.message}")
            }
        }
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
        val target = resolvePath(path)
        withContext(Dispatchers.IO) {
            FsUtil.write(target, content, append).onFailure { e ->
                fail("Failed to write to $path: ${e.message}")
            }
        }
    }

    /**
     * Reads text content from a file.
     */
    suspend fun readAsString(path:String):String {
        val target = resolvePath(path)
        return withContext(Dispatchers.IO) {
            FsUtil.readAsString(target).getOrElse { e ->
                fail("Failed to read $path: ${e.message}")
            }
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
     * @param kind The kind of paths to return (default: File)
     * @return List of relative paths matching the patterns
     */
    suspend fun glob(
        includes: List<String>,
        excludes: List<String> = emptyList(),
        baseDir: String? = null,
        kind: FsKind = FsKind.File
    ): List<String> {
        val rootPath = resolvePath(baseDir ?: "")
        return withContext(Dispatchers.IO) {
            FsUtil.glob(includes, excludes, rootPath, kind).getOrElse { e ->
                fail("Failed to find files with glob: ${e.message}")
            }
        }
    }

    /**
     * Finds files matching a single ant-style glob pattern.
     *
     * @param pattern The pattern to match
     * @param baseDir Base directory to search from (default: current working directory)
     * @param kind The kind of paths to return (default: File)
     * @return List of relative paths matching the pattern
     */
    suspend fun glob(pattern: String, baseDir: String? = null, kind: FsKind = FsKind.File): List<String> {
        val rootPath = resolvePath(baseDir ?: "")
        return withContext(Dispatchers.IO) {
            FsUtil.glob(pattern, rootPath, kind).getOrElse { e ->
                fail("Failed to find files with glob pattern $pattern: ${e.message}")
            }
        }
    }

}
