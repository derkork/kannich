package dev.kannich.trivy

import dev.kannich.stdlib.fail
import dev.kannich.stdlib.tools.Cache
import dev.kannich.stdlib.tools.Compressor
import dev.kannich.stdlib.tools.Fs
import dev.kannich.stdlib.tools.Shell
import dev.kannich.stdlib.tools.Web
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Provides Trivy security scanning for Kannich pipelines.
 * Downloads and installs the specified Trivy version on first use.
 *
 * Trivy is a comprehensive security scanner that can scan:
 * - Container images
 * - Filesystems
 * - Git repositories
 * - Kubernetes clusters
 * - And more
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val trivy = Trivy("0.58.0")
 *
 *     val scan = job("Security Scan") {
 *         // Scan a container image
 *         trivy.exec("image", "myapp:latest")
 *
 *         // Scan the filesystem
 *         trivy.exec("fs", ".")
 *
 *         // Scan with custom options
 *         trivy.exec("image", "--severity", "HIGH,CRITICAL", "--exit-code", "1", "myapp:latest")
 *     }
 * }
 * ```
 */
class Trivy(val version: String) {
    private val logger: Logger = LoggerFactory.getLogger(Trivy::class.java)

    companion object {
        private const val CACHE_KEY = "trivy"
    }

    /**
     * Gets the Trivy installation directory path inside the container.
     */
    suspend fun home(): String =
        Cache.path("$CACHE_KEY/trivy-$version")

    /**
     * Ensures Trivy is installed in the Cache.
     * Downloads from GitHub releases if not already present.
     */
    private suspend fun ensureInstalled() {
        val cacheKey = "$CACHE_KEY/trivy-$version"

        if (Cache.exists(cacheKey)) {
            logger.debug("Trivy $version is already installed.")
            return
        }

        logger.info("Trivy $version is not installed, downloading.")

        // Ensure trivy cache directory exists
        Cache.ensureDir(CACHE_KEY)

        // Create version-specific directory
        val trivyDir = Cache.path(cacheKey)
        Fs.mkdir(trivyDir)

        // Download and extract Trivy
        val downloadUrl = getDownloadUrl(version)
        val archive = Web.download(downloadUrl)
        Compressor.extract(archive, trivyDir)

        // Verify extraction succeeded - trivy binary should exist
        val trivyBinary = "$trivyDir/trivy"
        if (!Fs.exists(trivyBinary)) {
            fail("Trivy extraction failed: expected binary $trivyBinary not found")
        }

        logger.info("Successfully installed Trivy $version.")
    }

    /**
     * Executes Trivy with the given arguments.
     * Throws JobFailedException if the execution fails.
     *
     * @param args Arguments to pass to Trivy
     * @throws dev.kannich.stdlib.JobFailedException if the command fails
     */
    suspend fun exec(vararg args: String) {
        ensureInstalled()

        val homeDir = home()
        val trivyBinary = "$homeDir/trivy"

        // Set up cache directory for Trivy's vulnerability database
        val dbCacheKey = "$CACHE_KEY/db"
        Cache.ensureDir(dbCacheKey)
        val dbCachePath = Cache.path(dbCacheKey)

        val allArgs = listOf("--cache-dir", dbCachePath) + args.toList()
        val result = Shell.exec(trivyBinary, *allArgs.toTypedArray())

        if (!result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("Trivy command failed: $errorMessage")
        }
    }

    /**
     * Gets the download URL for the specified Trivy version.
     * Uses GitHub releases from aquasecurity/trivy.
     */
    private fun getDownloadUrl(version: String): String {
        // Trivy releases are available on GitHub
        // Format: trivy_{version}_Linux-64bit.tar.gz
        return "https://github.com/aquasecurity/trivy/releases/download/v$version/trivy_${version}_Linux-64bit.tar.gz"
    }
}
