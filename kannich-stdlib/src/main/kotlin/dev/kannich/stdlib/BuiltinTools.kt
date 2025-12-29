package dev.kannich.stdlib

import dev.kannich.stdlib.context.JobExecutionContext

/**
 * Built-in tool for filesystem operations.
 * Provides a clean API for common file/directory operations.
 *
 * Some operations support shell glob patterns (*, ?, [...]) in source paths.
 * Whitespace and shell metacharacters are automatically escaped while preserving globs.
 */
class FsTool {
    private val shell = ShellTool()

    /**
     * Escapes shell metacharacters while preserving glob patterns (*, ?, [...]).
     * This allows paths with spaces to work while still supporting glob expansion.
     */
    private fun escapeForGlob(path: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < path.length) {
            val c = path[i]
            when (c) {
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

    /**
     * Creates a temporary directory and returns its path.
     *
     * @param prefix Optional prefix for the temp directory name
     * @return The path to the created temporary directory
     * @throws JobFailedException if creation fails
     */
    fun mktemp(prefix: String = "kannich"): String {
        val result = shell.execShell("mktemp -d -t '$prefix.XXXXXX'")
        if (!result.success) {
            throw JobFailedException("Failed to create temp directory: ${result.stderr}")
        }
        return result.stdout.trim()
    }

    /**
     * Creates a directory and all parent directories if they don't exist.
     *
     * @param path The directory path to create
     * @throws JobFailedException if creation fails
     */
    fun mkdir(path: String) {
        val result = shell.execShell("mkdir -p '$path'")
        if (!result.success) {
            throw JobFailedException("Failed to create directory $path: ${result.stderr}")
        }
    }

    /**
     * Copies files or directories to a destination.
     *
     * @param src The source path (supports glob patterns like *.txt, /path/files-*)
     * @param dest The destination path (no glob support, receives the files)
     * @param recursive Whether to copy directories recursively (default: true)
     * @throws JobFailedException if copy fails
     */
    fun copy(src: String, dest: String, recursive: Boolean = true) {
        val flags = if (recursive) "-r" else ""
        val result = shell.execShell("cp $flags ${escapeForGlob(src)} '$dest'")
        if (!result.success) {
            throw JobFailedException("Failed to copy $src to $dest: ${result.stderr}")
        }
    }

    /**
     * Moves files or directories to a destination.
     *
     * @param src The source path (supports glob patterns like *.txt, /path/files-*)
     * @param dest The destination path (no glob support, receives the files)
     * @throws JobFailedException if move fails
     */
    fun move(src: String, dest: String) {
        val result = shell.execShell("mv ${escapeForGlob(src)} '$dest'")
        if (!result.success) {
            throw JobFailedException("Failed to move $src to $dest: ${result.stderr}")
        }
    }

    /**
     * Deletes files or directories recursively.
     *
     * @param path The path to delete (supports glob patterns like *.txt, /path/files-*)
     * @throws JobFailedException if deletion fails
     */
    fun delete(path: String) {
        val result = shell.execShell("rm -rf ${escapeForGlob(path)}")
        if (!result.success) {
            throw JobFailedException("Failed to delete $path: ${result.stderr}")
        }
    }

    /**
     * Checks if a path exists.
     *
     * @param path The exact path to check (no glob support)
     * @return true if the path exists
     */
    fun exists(path: String): Boolean {
        return shell.execShell("test -e '$path'").success
    }

    /**
     * Checks if a path is a directory.
     *
     * @param path The exact path to check (no glob support)
     * @return true if the path is a directory
     */
    fun isDirectory(path: String): Boolean {
        return shell.execShell("test -d '$path'").success
    }

    /**
     * Checks if a path is a file.
     *
     * @param path The exact path to check (no glob support)
     * @return true if the path is a file
     */
    fun isFile(path: String): Boolean {
        return shell.execShell("test -f '$path'").success
    }
}

/**
 * Built-in tool for downloading files.
 * Downloads to a temporary directory by default.
 */
class DownloadTool {
    private val fs = FsTool()
    private val shell = ShellTool()

    /**
     * Downloads a file from a URL to a temporary directory.
     *
     * @param url The URL to download from
     * @param filename Optional filename. If not provided, derived from URL or uses a generated name.
     * @return The path to the downloaded file
     * @throws JobFailedException if the download fails
     */
    fun download(url: String, filename: String? = null): String {
        // Create temp directory for download
        val tempDir = fs.mktemp("download")

        // Determine filename
        val actualFilename = filename
            ?: url.substringAfterLast('/').takeIf { it.isNotBlank() && !it.contains('?') }
            ?: "download-${System.currentTimeMillis()}"

        val outputPath = "$tempDir/$actualFilename"

        // Download with curl
        // -s: silent, -S: show errors, -L: follow redirects, -f: fail on HTTP errors, -o: output file
        val result = shell.exec("curl", "-sSLf", "-o", outputPath, url)
        if (!result.success) {
            fs.delete(tempDir)
            throw JobFailedException("Failed to download $url: ${result.stderr}")
        }

        return outputPath
    }

    /**
     * Downloads a file from a URL to a specific destination.
     *
     * @param url The URL to download from
     * @param dest The destination path (file path or directory)
     * @param filename Optional filename when dest is a directory
     * @return The path to the downloaded file
     * @throws JobFailedException if the download fails
     */
    fun downloadTo(url: String, dest: String, filename: String? = null): String {
        // Determine the output path
        val outputPath = if (filename != null) {
            "$dest/$filename"
        } else if (fs.isDirectory(dest)) {
            val name = url.substringAfterLast('/').takeIf { it.isNotBlank() && !it.contains('?') }
                ?: "download-${System.currentTimeMillis()}"
            "$dest/$name"
        } else {
            dest
        }

        // Ensure parent directory exists
        val parentDir = outputPath.substringBeforeLast('/')
        fs.mkdir(parentDir)

        // Download with curl
        val result = shell.exec("curl", "-sSLf", "-o", outputPath, url)
        if (!result.success) {
            throw JobFailedException("Failed to download $url: ${result.stderr}")
        }

        return outputPath
    }
}

/**
 * Built-in tool for extracting archives.
 * Automatically detects archive format from file extension.
 */
class ExtractTool {
    private val fs = FsTool()
    private val shell = ShellTool()

    /**
     * Extracts an archive to the specified destination.
     * Automatically detects the archive format from the file extension.
     *
     * Supported formats:
     * - .tar.gz, .tgz - gzipped tar
     * - .tar.xz, .txz - xz-compressed tar
     * - .tar.bz2, .tbz2 - bzip2-compressed tar
     * - .tar - uncompressed tar
     * - .zip - zip archive
     * - .gz - gzip (single file, extracts in place)
     *
     * @param archive The path to the archive file
     * @param dest The destination directory to extract to
     * @throws JobFailedException if extraction fails or format is unsupported
     */
    fun extract(archive: String, dest: String) {
        // Ensure destination directory exists
        fs.mkdir(dest)

        // Determine extraction command based on extension
        val lowerArchive = archive.lowercase()
        val result = when {
            lowerArchive.endsWith(".tar.gz") || lowerArchive.endsWith(".tgz") ->
                shell.exec("tar", "xzf", archive, "-C", dest)
            lowerArchive.endsWith(".tar.xz") || lowerArchive.endsWith(".txz") ->
                shell.exec("tar", "xJf", archive, "-C", dest)
            lowerArchive.endsWith(".tar.bz2") || lowerArchive.endsWith(".tbz2") ->
                shell.exec("tar", "xjf", archive, "-C", dest)
            lowerArchive.endsWith(".tar") ->
                shell.exec("tar", "xf", archive, "-C", dest)
            lowerArchive.endsWith(".zip") ->
                shell.exec("unzip", "-q", "-o", archive, "-d", dest)
            lowerArchive.endsWith(".gz") ->
                // For .gz files (non-tar), gunzip in place - copy first to dest
                shell.execShell("cp '$archive' '$dest/' && gunzip -f '$dest/${archive.substringAfterLast('/')}'")
            else ->
                throw JobFailedException("Unsupported archive format: $archive")
        }

        if (!result.success) {
            throw JobFailedException("Failed to extract $archive: ${result.stderr}")
        }
    }

    /**
     * Downloads and extracts an archive in one operation.
     * More efficient than download + extract as it pipes directly without intermediate file.
     *
     * @param url The URL to download from
     * @param dest The destination directory to extract to
     * @param format The archive format (tar.gz, tar.xz, tar.bz2, tar, zip). If null, auto-detected from URL.
     * @throws JobFailedException if download or extraction fails
     */
    fun downloadAndExtract(url: String, dest: String, format: String? = null) {
        // Ensure destination directory exists
        fs.mkdir(dest)

        // Determine format from URL if not provided
        val detectedFormat = format ?: detectFormat(url)

        // Build piped command for efficiency
        val extractCmd = when (detectedFormat) {
            "tar.gz", "tgz" -> "tar xzf - -C '$dest'"
            "tar.xz", "txz" -> "tar xJf - -C '$dest'"
            "tar.bz2", "tbz2" -> "tar xjf - -C '$dest'"
            "tar" -> "tar xf - -C '$dest'"
            "zip" -> {
                // zip doesn't support piping, need to download first
                val tempDir = fs.mktemp("extract")
                val tempFile = "$tempDir/archive.zip"
                val result = shell.execShell("curl -sSLf -o '$tempFile' '$url' && unzip -q -o '$tempFile' -d '$dest'")
                fs.delete(tempDir)
                if (!result.success) {
                    throw JobFailedException("Failed to download and extract $url: ${result.stderr}")
                }
                return
            }
            else -> throw JobFailedException("Unsupported archive format: $detectedFormat")
        }

        val result = shell.execShell("curl -sSLf '$url' | $extractCmd")
        if (!result.success) {
            throw JobFailedException("Failed to download and extract $url: ${result.stderr}")
        }
    }

    private fun detectFormat(url: String): String {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.endsWith(".tar.gz") || lowerUrl.endsWith(".tgz") -> "tar.gz"
            lowerUrl.endsWith(".tar.xz") || lowerUrl.endsWith(".txz") -> "tar.xz"
            lowerUrl.endsWith(".tar.bz2") || lowerUrl.endsWith(".tbz2") -> "tar.bz2"
            lowerUrl.endsWith(".tar") -> "tar"
            lowerUrl.endsWith(".zip") -> "zip"
            lowerUrl.endsWith(".gz") -> "gz"
            // Some URLs don't have extensions (like Adoptium API), assume tar.gz
            else -> "tar.gz"
        }
    }
}

/**
 * Built-in tool for managing the Kannich cache.
 * Provides methods to store, retrieve, and clear cached items.
 *
 * Cache structure: /kannich/cache/{key}/...
 */
class CacheTool {
    private val fs = FsTool()
    private val shell = ShellTool()

    /**
     * Checks if a cache key exists.
     *
     * @param key The cache key (e.g., "java/temurin-21", "maven/apache-maven-3.9.6")
     * @return true if the cached item exists
     */
    fun exists(key: String): Boolean {
        return fs.exists(path(key))
    }

    /**
     * Gets the full path to a cached item.
     *
     * @param key The cache key
     * @return The full path to the cached item
     */
    fun path(key: String): String {
        val ctx = JobExecutionContext.current()
        return "${ctx.pipelineContext.cacheDir}/$key"
    }

    /**
     * Gets the base cache directory path.
     *
     * @return The cache directory path
     */
    fun baseDir(): String {
        val ctx = JobExecutionContext.current()
        return ctx.pipelineContext.cacheDir
    }

    /**
     * Moves a file or directory into the cache.
     *
     * @param sourcePath The source path to move from
     * @param key The cache key to move to
     * @throws JobFailedException if the move fails
     */
    fun put(sourcePath: String, key: String) {
        val cachePath = path(key)
        val parentDir = cachePath.substringBeforeLast('/')
        fs.mkdir(parentDir)
        fs.move(sourcePath, cachePath)
    }

    /**
     * Clears a specific cache key or the entire cache.
     *
     * @param key The cache key to clear, or null to clear the entire cache
     * @throws JobFailedException if clearing fails
     */
    fun clear(key: String? = null) {
        if (key != null) {
            fs.delete(path(key))
        } else {
            // Clear all contents but keep the cache directory itself
            val result = shell.execShell("rm -rf ${baseDir()}/*")
            if (!result.success) {
                throw JobFailedException("Failed to clear cache: ${result.stderr}")
            }
        }
    }

    /**
     * Ensures a directory exists in the cache.
     *
     * @param key The cache key (directory path relative to cache root)
     * @throws JobFailedException if directory creation fails
     */
    fun ensureDir(key: String) {
        fs.mkdir(path(key))
    }
}
