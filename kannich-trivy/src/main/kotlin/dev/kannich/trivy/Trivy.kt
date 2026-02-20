package dev.kannich.trivy

import dev.kannich.stdlib.Tool
import dev.kannich.stdlib.fail
import dev.kannich.tools.Cache
import dev.kannich.tools.Compressor
import dev.kannich.tools.Fs
import dev.kannich.tools.Shell
import dev.kannich.tools.Web
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
class Trivy(val version: String) : Tool {
    private val logger: Logger = LoggerFactory.getLogger(Trivy::class.java)

    companion object {
        private const val CACHE_KEY = "trivy"
    }

    /**
     * Gets the Trivy installation directory path inside the container.
     */
    suspend fun home(): String =
        Cache.path("$CACHE_KEY/trivy-$version")


    override suspend fun getToolPaths() = listOf(home())

    /**
     * Ensures Trivy is installed in the Cache.
     * Downloads from GitHub releases if not already present.
     */
    override suspend fun ensureInstalled() {
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
     *
     * @param args Arguments to pass to Trivy
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
     * Scans a filesystem for vulnerabilities and generates an HTML report.
     *
     * @param reportPath Path to save the HTML report, defaults to 'target/report.html'
     * @param severity Severity level to filter vulnerabilities, defaults to 'CRITICAL,HIGH'
     */
    suspend fun scanFs(reportPath: String = "target/report.html", severity: String = "CRITICAL,HIGH") {
        // ensure parent directory of output file exists
        val parentDir = Fs.getParent(reportPath)
        Fs.mkdir(parentDir)

        exec(
            "fs",
            "--quiet",
            "--scanners",
            "vuln",
            ".",
            "--severity", severity,
            "--ignore-unfixed",
            "--exit-code", "1",
            "--format",
            "template",
            "--template",
            "@${home()}/contrib/html.tpl", "-o", reportPath
        )
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
