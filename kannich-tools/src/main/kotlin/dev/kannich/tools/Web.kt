package dev.kannich.tools

import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.fail
import java.security.MessageDigest

/**
 * Built-in tool for downloading files.
 */
object Web {

    private const val DOWNLOAD_CACHE_KEY = "kannich-downloads"
    private const val METADATA_SUFFIX = ".meta"

    /**
     * Computes SHA-256 hash of a string for cache key generation.
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Checks if cached file is still fresh by comparing ETag and Last-Modified headers.
     * Returns true if cache is valid, false if it needs to be refreshed.
     */
    private suspend fun isCacheFresh(url: String, metadataFile: String): Boolean {
        if (!Fs.exists(metadataFile)) {
            return false
        }

        // Read stored metadata
        val metadata = Fs.readAsString(metadataFile)
        val storedEtag = metadata.lines().find { it.startsWith("ETag:") }?.substringAfter("ETag:").orEmpty().trim()
        val storedLastModified = metadata.lines().find { it.startsWith("Last-Modified:") }?.substringAfter("Last-Modified:").orEmpty().trim()

        // If no metadata was stored, consider cache stale
        if (storedEtag.isEmpty() && storedLastModified.isEmpty()) {
            return false
        }

        // Check freshness with HEAD request
        val args = mutableListOf( "-sSLI", url)
        if (storedEtag.isNotEmpty()) {
            args.addAll(listOf("-H", "If-None-Match: $storedEtag"))
        }
        if (storedLastModified.isNotEmpty()) {
            args.addAll(listOf("-H", "If-Modified-Since: $storedLastModified"))
        }

        val result = Shell.exec("curl", *args.toTypedArray())
        // 304 Not Modified means cache is fresh
        return result.success && result.stdout.contains("304 Not Modified")
    }

    /**
     * Downloads and caches a file from a URL.
     * Returns the path to the cached file.
     */
    private suspend fun downloadAndCache(url: String): String {
        Cache.ensureDir(DOWNLOAD_CACHE_KEY)

        val urlHash = sha256(url)
        val cachedFile = Cache.path("$DOWNLOAD_CACHE_KEY/$urlHash")
        val metadataFile = Cache.path("$DOWNLOAD_CACHE_KEY/$urlHash$METADATA_SUFFIX")

        // Check if cache exists and is fresh
        if (Fs.exists(cachedFile) && isCacheFresh(url, metadataFile)) {
            return cachedFile
        }

        // Download with headers to cache
        // -D: dump headers to file, -s: silent, -S: show errors, -L: follow redirects, -f: fail on HTTP errors
        val headerFile = "$cachedFile.headers"
        val result = Shell.exec("curl", "-D", headerFile, "-sSLf", "-o", cachedFile, url)

        if (!result.success) {
            // Clean up failed download
            if (Fs.exists(cachedFile)) {
                Fs.delete(cachedFile)
            }
            if (Fs.exists(headerFile)) {
                Fs.delete(headerFile)
            }
            fail("Failed to download $url: ${result.stderr}")
        }

        // Extract and store ETag and Last-Modified headers
        if (Fs.exists(headerFile)) {
            val headers = Fs.readAsString(headerFile)
            val etag = headers.lines().find { it.startsWith("ETag:", ignoreCase = true) }?.substringAfter(":").orEmpty().trim()
            val lastModified = headers.lines().find { it.startsWith("Last-Modified:", ignoreCase = true) }?.substringAfter(":").orEmpty().trim()

            val metadata = buildString {
                if (etag.isNotEmpty()) appendLine("ETag: $etag")
                if (lastModified.isNotEmpty()) appendLine("Last-Modified: $lastModified")
            }

            if (metadata.isNotEmpty()) {
                Fs.write(metadataFile, metadata)
            }

            // Clean up header file
            Fs.delete(headerFile)
        }

        return cachedFile
    }

    /**
     * Downloads a file from a URL to a temporary directory. When the download fails, cleans up any leftover files.
     * On success, returns the path to the downloaded file. The file will be automatically deleted when the job ends.
     *
     * Uses a persistent cache at /kannich/cache/downloads to avoid re-downloading the same URL.
     * Cache freshness is validated using ETag and Last-Modified HTTP headers.
     *
     * @param url The URL to download from
     * @param filename Optional filename. If not provided, derived from URL or uses a generated name.
     * @return The path to the downloaded file
     * @throws dev.kannich.stdlib.JobFailedException if the download fails
     */
    suspend fun download(url: String, filename: String? = null): String {
        // Determine filename
        val actualFilename = filename
            ?: url.substringAfterLast('/').takeIf { it.isNotBlank() && !it.contains('?') }
            ?: "download-${System.currentTimeMillis()}"

        // Get cached file (downloading if necessary)
        val cachedFile = downloadAndCache(url)

        // Copy from cache to temp location
        val tempDir = Fs.mktemp("download")
        JobContext.current().onCleanup { Fs.delete(tempDir) }
        val outputPath = "$tempDir/$actualFilename"
        Fs.copy(cachedFile, outputPath)

        return outputPath
    }

    /**
     * Downloads a file from a URL to a specific destination. The file will be automatically deleted when the job ends.
     *
     * Uses a persistent cache at /kannich/cache/downloads to avoid re-downloading the same URL.
     * Cache freshness is validated using ETag and Last-Modified HTTP headers.
     *
     * @param url The URL to download from
     * @param dest The destination path (file path or directory)
     * @param filename Optional filename when dest is a directory
     * @return The path to the downloaded file
     * @throws dev.kannich.stdlib.JobFailedException if the download fails
     */
    suspend fun downloadTo(url: String, dest: String, filename: String? = null): String {
        // Determine the output path
        val outputPath = if (filename != null) {
            "$dest/$filename"
        } else if (Fs.isDirectory(dest)) {
            val name = url.substringAfterLast('/').takeIf { it.isNotBlank() && !it.contains('?') }
                ?: "download-${System.currentTimeMillis()}"
            "$dest/$name"
        } else {
            dest
        }

        // Ensure parent directory exists
        val parentDir = outputPath.substringBeforeLast('/')
        Fs.mkdir(parentDir)

        // Get cached file (downloading if necessary)
        val cachedFile = downloadAndCache(url)

        // Copy from cache to destination
        Fs.copy(cachedFile, outputPath)

        // Cleanup when job ends
        JobContext.current().onCleanup { Fs.delete(outputPath) }

        return outputPath
    }
}
