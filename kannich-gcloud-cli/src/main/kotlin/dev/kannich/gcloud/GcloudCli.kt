package dev.kannich.gcloud

import dev.kannich.stdlib.ExecResult
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
 * Provides Google Cloud CLI for Kannich pipelines.
 * Downloads and installs the specified Google Cloud CLI version on first use.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val gcloud = GcloudCli("490.0.0")
 *     job("Check gcloud CLI") {
 *         gcloud.exec("--version")
 *     }
 * }
 * ```
 */
class GcloudCli(val version: String) : Tool {
    private val logger: Logger = LoggerFactory.getLogger(GcloudCli::class.java)

    companion object {
        private const val CACHE_KEY = "gcloud"
    }

    /**
     * Gets the gcloud CLI installation directory path inside the container.
     */
    suspend fun home(): String = Cache.path("$CACHE_KEY/gcloud-$version")

    override suspend fun getToolPaths() = listOf("${home()}/google-cloud-sdk/bin")

    /**
     * Ensures Google Cloud CLI is installed in the Cache.
     * Downloads from Google if not already present.
     */
    override suspend fun ensureInstalled() {
        val cacheKey = "$CACHE_KEY/gcloud-$version"

        if (Cache.exists(cacheKey)) {
            logger.debug("Google Cloud CLI $version is already installed.")
            return
        }

        logger.info("Google Cloud CLI $version is not installed, downloading.")

        // Ensure cache directory exists
        Cache.ensureDir(CACHE_KEY)

        // Create version-specific directory
        val gcloudDir = Cache.path(cacheKey)
        Fs.mkdir(gcloudDir)

        // Download and extract Google Cloud CLI. The archive contains a 'google-cloud-sdk' directory.
        val downloadUrl = getDownloadUrl(version)
        val archive = Web.download(downloadUrl)
        Compressor.extract(archive, gcloudDir)

        // Verify extraction succeeded - gcloud binary should exist at google-cloud-sdk/bin/gcloud
        val gcloudBinary = "$gcloudDir/google-cloud-sdk/bin/gcloud"
        if (!Fs.exists(gcloudBinary)) {
            fail("Google Cloud CLI extraction failed: expected binary $gcloudBinary not found")
        }

        // Set executable bit just in case
        Shell.exec("chmod", "+x", gcloudBinary)

        logger.info("Successfully installed Google Cloud CLI $version.")
    }

    /**
     * Executes gcloud CLI with the given arguments.
     *
     * @param args Arguments to pass to `gcloud`
     */
    override suspend fun exec(vararg args: String, silent: Boolean, allowFailure: Boolean): ExecResult {
        ensureInstalled()

        val homeDir = home()
        val gcloudBinary = "$homeDir/google-cloud-sdk/bin/gcloud"

        val result = Shell.exec(gcloudBinary, *args)

        if (!allowFailure && !result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("gcloud CLI command failed: $errorMessage")
        }

        return result
    }

    /**
     * Gets the download URL for the specified Google Cloud CLI version.
     * Uses dl.google.com.
     */
    private fun getDownloadUrl(version: String): String {
        // Format: google-cloud-cli-{version}-linux-x86_64.tar.gz
        return "https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-cli-${version}-linux-x86_64.tar.gz"
    }
}
