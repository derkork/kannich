package dev.kannich.terraform

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
 * Provides Terraform infrastructure management for Kannich pipelines.
 * Downloads and installs the specified Terraform version on first use.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val terraform = Terraform("1.11.0")
 *
 *     val deploy = job("Deploy Infrastructure") {
 *         terraform.exec("init")
 *     }
 * }
 * ```
 */
class Terraform(val version: String) : Tool {
    private val logger: Logger = LoggerFactory.getLogger(Terraform::class.java)

    companion object {
        private const val CACHE_KEY = "terraform"
    }

    /**
     * Gets the Terraform installation directory path inside the container.
     */
    suspend fun home(): String =
        Cache.path("$CACHE_KEY/terraform-$version")


    override suspend fun getToolPaths() = listOf(home())

    /**
     * Ensures Terraform is installed in the Cache.
     * Downloads from HashiCorp Releases if not already present.
     */
    override suspend fun ensureInstalled() {
        val cacheKey = "$CACHE_KEY/terraform-$version"

        if (Cache.exists(cacheKey)) {
            logger.debug("Terraform $version is already installed.")
            return
        }

        logger.info("Terraform $version is not installed, downloading.")

        // Ensure terraform cache directory exists
        Cache.ensureDir(CACHE_KEY)

        // Create version-specific directory
        val terraformDir = Cache.path(cacheKey)
        Fs.mkdir(terraformDir)

        // Download and extract Terraform
        val downloadUrl = getDownloadUrl(version)
        val archive = Web.download(downloadUrl)
        Compressor.extract(archive, terraformDir)

        // Verify extraction succeeded - terraform binary should exist
        val terraformBinary = "$terraformDir/terraform"
        if (!Fs.exists(terraformBinary)) {
            fail("Terraform extraction failed: expected binary $terraformBinary not found")
        }

        // Set executable bit - sometimes zip files don't preserve it well
        Shell.exec("chmod", "+x", terraformBinary)

        logger.info("Successfully installed Terraform $version.")
    }

    /**
     * Executes Terraform with the given arguments.
     *
     * @param args Arguments to pass to Terraform
     */
    override suspend fun exec(vararg args: String, silent: Boolean, allowFailure: Boolean) : ExecResult {
        ensureInstalled()

        val homeDir = home()
        val terraformBinary = "$homeDir/terraform"

        val result = Shell.exec(terraformBinary, *args, silent = silent)

        if (!allowFailure && !result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("Terraform command failed: $errorMessage")
        }

        return result
    }


    /**
     * Gets the download URL for the specified Terraform version.
     * Uses HashiCorp releases.
     */
    private fun getDownloadUrl(version: String): String {
        // HashiCorp releases are available at releases.hashicorp.com
        // Format: terraform_{version}_linux_amd64.zip
        return "https://releases.hashicorp.com/terraform/$version/terraform_${version}_linux_amd64.zip"
    }
}
