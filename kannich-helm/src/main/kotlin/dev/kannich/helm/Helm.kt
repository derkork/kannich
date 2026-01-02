package dev.kannich.helm

import dev.kannich.stdlib.fail
import dev.kannich.stdlib.tools.CacheTool
import dev.kannich.stdlib.tools.ExtractTool
import dev.kannich.stdlib.tools.FsTool
import dev.kannich.stdlib.tools.ShellTool
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Provides Helm for Kannich pipelines.
 * Downloads and installs the specified Helm version on first use.
 *
 * Helm is the package manager for Kubernetes that helps you:
 * - Install and manage Kubernetes applications
 * - Package and share applications as Helm charts
 * - Manage releases and rollbacks
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val helm = Helm("3.14.0")
 *
 *     val deploy = job("Deploy to Kubernetes") {
 *         // Install a chart
 *         helm.exec("install", "my-release", "my-chart")
 *
 *         // Upgrade a release
 *         helm.exec("upgrade", "--install", "my-release", "my-chart")
 *
 *         // List releases
 *         helm.exec("list")
 *
 *         // Add a repository
 *         helm.exec("repo", "add", "bitnami", "https://charts.bitnami.com/bitnami")
 *     }
 * }
 * ```
 */
class Helm(val version: String) {
    private val logger: Logger = LoggerFactory.getLogger(Helm::class.java)
    private val cache = CacheTool()
    private val extract = ExtractTool()
    private val fs = FsTool()
    private val shell = ShellTool()

    companion object {
        private const val CACHE_KEY = "helm"
    }

    /**
     * Gets the Helm installation directory path inside the container.
     */
    fun home(): String =
        cache.path("$CACHE_KEY/helm-$version")

    /**
     * Ensures Helm is installed in the cache.
     * Downloads from get.helm.sh if not already present.
     */
    private fun ensureInstalled() {
        val cacheKey = "$CACHE_KEY/helm-$version"

        if (cache.exists(cacheKey)) {
            logger.debug("Helm $version is already installed.")
            return
        }

        logger.info("Helm $version is not installed, downloading.")

        // Ensure helm cache directory exists
        cache.ensureDir(CACHE_KEY)

        // Create version-specific directory
        val helmDir = cache.path(cacheKey)
        fs.mkdir(helmDir)

        // Download and extract Helm
        val downloadUrl = getDownloadUrl(version)
        extract.downloadAndExtract(downloadUrl, helmDir)

        // Helm archives extract to linux-amd64/helm, move it to the right place
        val extractedBinary = "$helmDir/linux-amd64/helm"
        val targetBinary = "$helmDir/helm"
        if (fs.exists(extractedBinary)) {
            fs.move(extractedBinary, targetBinary)
            fs.delete("$helmDir/linux-amd64")
        }

        // Verify extraction succeeded - helm binary should exist
        if (!fs.exists(targetBinary)) {
            fail("Helm extraction failed: expected binary $targetBinary not found")
        }

        logger.info("Successfully installed Helm $version.")
    }

    /**
     * Executes Helm with the given arguments.
     * Throws JobFailedException if the execution fails.
     *
     * @param args Arguments to pass to Helm
     * @throws dev.kannich.stdlib.JobFailedException if the command fails
     */
    fun exec(vararg args: String) {
        ensureInstalled()

        val homeDir = home()
        val helmBinary = "$homeDir/helm"

        val result = shell.exec(helmBinary, *args)

        if (!result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("Helm command failed: $errorMessage")
        }
    }

    /**
     * Gets the download URL for the specified Helm version.
     * Uses the official Helm download site.
     */
    private fun getDownloadUrl(version: String): String {
        // Helm releases are available at get.helm.sh
        // Format: helm-v{version}-linux-amd64.tar.gz
        return "https://get.helm.sh/helm-v$version-linux-amd64.tar.gz"
    }
}
