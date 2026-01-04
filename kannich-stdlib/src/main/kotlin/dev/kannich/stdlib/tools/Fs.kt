package dev.kannich.stdlib.tools

import dev.kannich.stdlib.context.currentJobExecutionContext
import dev.kannich.stdlib.fail
import dev.kannich.stdlib.util.AntPathMatcher
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream

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
     * Creates a temporary directory and returns its path.
     *
     * @param prefix Optional prefix for the temp directory name
     * @return The path to the created temporary directory
     * @throws dev.kannich.stdlib.JobFailedException if creation fails
     */
    suspend fun mktemp(prefix: String = "kannich"): String {
        val result = Shell.execShell("mktemp -d -t '$prefix.XXXXXX'")
        if (!result.success) {
            fail("Failed to create temp directory: ${result.stderr}")
        }
        return result.stdout.trim()
    }

    /**
     * Creates a directory and all parent directories if they don't exist.
     *
     * @param path The directory path to create
     * @throws dev.kannich.stdlib.JobFailedException if creation fails
     */
    suspend fun mkdir(path: String) {
        val result = Shell.execShell("mkdir -p '$path'")
        if (!result.success) {
            fail("Failed to create directory $path: ${result.stderr}")
        }
    }

    /**
     * Copies files or directories to a destination.
     *
     * @param src The source path (supports glob patterns like *.txt, /path/files-*)
     * @param dest The destination path (no glob support, receives the files)
     * @param recursive Whether to copy directories recursively (default: true)
     * @throws dev.kannich.stdlib.JobFailedException if copy fails
     */
    suspend fun copy(src: String, dest: String, recursive: Boolean = true) {
        val flags = if (recursive) "-r" else ""
        val result = Shell.execShell("cp $flags ${escapeForGlob(src)} '$dest'")
        if (!result.success) {
            fail("Failed to copy $src to $dest: ${result.stderr}")
        }
    }

    /**
     * Moves files or directories to a destination.
     *
     * @param src The source path (supports glob patterns like *.txt, /path/files-*)
     * @param dest The destination path (no glob support, receives the files)
     * @throws dev.kannich.stdlib.JobFailedException if move fails
     */
    suspend fun move(src: String, dest: String) {
        val result = Shell.execShell("mv ${escapeForGlob(src)} '$dest'")
        if (!result.success) {
            fail("Failed to move $src to $dest: ${result.stderr}")
        }
    }

    /**
     * Deletes files or directories recursively.
     *
     * @param path The path to delete (supports glob patterns like *.txt, /path/files-*)
     * @throws dev.kannich.stdlib.JobFailedException if deletion fails
     */
    suspend fun delete(path: String) {
        val result = Shell.execShell("rm -rf ${escapeForGlob(path)}")
        if (!result.success) {
            fail("Failed to delete $path: ${result.stderr}")
        }
    }

    /**
     * Checks if a path exists.
     *
     * @param path The exact path to check (no glob support)
     * @return true if the path exists
     */
    suspend fun exists(path: String): Boolean {
        return Shell.execShell("test -e '$path'").success
    }

    /**
     * Checks if a path is a directory.
     *
     * @param path The exact path to check (no glob support)
     * @return true if the path is a directory
     */
    suspend fun isDirectory(path: String): Boolean {
        return Shell.execShell("test -d '$path'").success
    }

    /**
     * Checks if a path is a file.
     *
     * @param path The exact path to check (no glob support)
     * @return true if the path is a file
     */
    suspend fun isFile(path: String): Boolean {
        return Shell.execShell("test -f '$path'").success
    }

    /**
     * Writes text content to a file.
     * Creates parent directories if needed. Overwrites an existing file.
     * Uses Docker's copy API for reliable handling of large content.
     *
     * @param path The file path to write to
     * @param content The text content to write
     * @param append If true, appends content to the file instead of overwriting
     * @throws dev.kannich.stdlib.JobFailedException if write fails
     */
    suspend fun write(path: String, content: String, append: Boolean = false) {
        write(path, ByteArrayInputStream(content.toByteArray()), append)
    }

    /**
     * Writes binary content from an input stream to a file.
     * Creates parent directories if needed. Overwrites an existing file.
     * Uses Docker's copy API for reliable streaming of large files.
     *
     * @param path The file path to write to (relative to working directory, or absolute)
     * @param content The input stream to read content from
     * @param append If true, appends content to the file instead of overwriting
     * @throws dev.kannich.stdlib.JobFailedException if write fails
     */
    suspend fun write(path: String, content: InputStream, append: Boolean = false) {
        // Ensure parent directory exists
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) {
            mkdir(parent)
        }
        val ctx = currentJobExecutionContext()
        // Make path absolute if it's relative
        val absolutePath = if (path.startsWith("/")) path else "${ctx.workingDir}/$path"
        if (append) {
            logger.info("Appending to $absolutePath")
        } else {
            logger.info("Writing to $absolutePath")
        }
        ctx.executor.writeFile(absolutePath, content, append)
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
        val workDir = baseDir ?: "."
        val matchingPaths = mutableListOf<String>()

        // Separate literal paths from patterns with wildcards
        val (literalPaths, patterns) = includes.partition { AntPathMatcher.isLiteralPath(it) }

        // For literal paths, just check if they exist (no find needed)
        for (path in literalPaths) {
            val fullPath = if (workDir == ".") path else "$workDir/$path"
            if (Shell.execShell("test -e '$fullPath'", silent = true).success) {
                val matchesExclude = excludes.isNotEmpty() &&
                                     AntPathMatcher.matchesAny(excludes, path)
                if (!matchesExclude) {
                    matchingPaths.add(path)
                }
            }
        }

        // For patterns with wildcards, use find with base path optimization
        if (patterns.isNotEmpty()) {
            val basePaths = AntPathMatcher.getBasePaths(patterns)

            val findCommand = if (basePaths.isEmpty() || basePaths.contains("")) {
                "find '$workDir' \\( -type f -o -type d \\) 2>/dev/null || true"
            } else {
                val paths = basePaths.joinToString(" ") {
                    if (workDir == ".") "'$it'" else "'$workDir/$it'"
                }
                "find $paths \\( -type f -o -type d \\) 2>/dev/null || true"
            }

            val findResult = Shell.execShell(findCommand, silent = true)
            val prefix = if (workDir == ".") "./" else "$workDir/"

            val allPaths = findResult.stdout.lines()
                .filter { it.isNotBlank() && it != workDir && it != "." }
                .map { it.removePrefix(prefix) }

            for (relativePath in allPaths) {
                val matchesInclude = AntPathMatcher.matchesAny(patterns, relativePath)
                val matchesExclude = excludes.isNotEmpty() &&
                                     AntPathMatcher.matchesAny(excludes, relativePath)
                if (matchesInclude && !matchesExclude) {
                    matchingPaths.add(relativePath)
                }
            }
        }

        return matchingPaths
    }

    /**
     * Finds files matching a single ant-style glob pattern.
     *
     * @param pattern The pattern to match
     * @param baseDir Base directory to search from (default: current working directory)
     * @return List of relative paths matching the pattern
     */
    suspend fun glob(pattern: String, baseDir: String? = null): List<String> {
        return glob(listOf(pattern), emptyList(), baseDir)
    }

    /**
     * Escapes shell metacharacters while preserving glob patterns (*, ?, [...]).
     * This allows paths with spaces to work while still supporting glob expansion.
     */
    private fun escapeForGlob(path: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < path.length) {
            when (val c = path[i]) {
                // Preserve glob characters
                '*', '?' -> sb.append(c)
                '[' -> {
                    // Preserve bracket expressions [...]
                    val closeBracket = path.indexOf(']', i + 1)
                    if (closeBracket != -1) {
                        sb.append(path.substring(i, closeBracket + 1))
                        i = closeBracket
                    } else {
                        sb.append("\\[")
                    }
                }
                // Escape shell metacharacters
                ' ', '\t', '\n', '(', ')', '<', '>', '&', ';', '|',
                '$', '`', '\\', '"', '\'', '!', '{', '}', '#' -> {
                    sb.append('\\')
                    sb.append(c)
                }
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}
