package dev.kannich.awscli

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
 * Provides AWS CLI v2 for Kannich pipelines.
 * Downloads and installs the specified AWS CLI version on first use.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val aws = AwsCli("2.17.44")
 *     job("Check AWS CLI") {
 *         aws.exec("--version")
 *     }
 * }
 * ```
 */
class AwsCli(val version: String) : Tool {
    private val logger: Logger = LoggerFactory.getLogger(AwsCli::class.java)

    companion object {
        private const val CACHE_KEY = "awscli"
    }

    /**
     * Gets the AWS CLI installation directory path inside the container.
     */
    suspend fun home(): String = Cache.path("$CACHE_KEY/awscli-$version")

    override suspend fun getToolPaths() = listOf("${home()}aws/dist")

    /**
     * Ensures AWS CLI is installed in the Cache.
     * Downloads from AWS if not already present.
     */
    override suspend fun ensureInstalled() {
        val cacheKey = "$CACHE_KEY/awscli-$version"

        if (Cache.exists(cacheKey)) {
            logger.debug("AWS CLI $version is already installed.")
            return
        }

        logger.info("AWS CLI $version is not installed, downloading.")

        // Ensure cache directory exists
        Cache.ensureDir(CACHE_KEY)

        // Create version-specific directory
        val awsCliDir = Cache.path(cacheKey)
        Fs.mkdir(awsCliDir)

        // Download and extract AWS CLI v2. The archive contains an 'aws' directory with 'dist/aws'
        val downloadUrl = getDownloadUrl(version)
        val archive = Web.download(downloadUrl)
        Compressor.extract(archive, awsCliDir)

        // Verify extraction succeeded - aws binary should exist at aws/dist/aws
        val awsBinary = "$awsCliDir/aws/dist/aws"
        if (!Fs.exists(awsBinary)) {
            fail("AWS CLI extraction failed: expected binary $awsBinary not found")
        }

        // Set executable bit just in case
        Shell.exec("chmod", "+x", awsBinary)

        logger.info("Successfully installed AWS CLI $version.")
    }

    /**
     * Executes AWS CLI with the given arguments.
     *
     * @param args Arguments to pass to `aws`
     */
    suspend fun exec(vararg args: String) {
        ensureInstalled()

        val homeDir = home()
        val awsBinary = "$homeDir/aws/dist/aws"

        val result = Shell.exec(awsBinary, *args)

        if (!result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${'$'}{result.exitCode}" }
            fail("AWS CLI command failed: ${'$'}errorMessage")
        }
    }

    /**
     * Gets the download URL for the specified AWS CLI version.
     * Uses awscli.amazonaws.com.
     */
    private fun getDownloadUrl(version: String): String {
        // Format: awscli-exe-linux-x86_64-{version}.zip
        return "https://awscli.amazonaws.com/awscli-exe-linux-x86_64-${version}.zip"
    }
}
