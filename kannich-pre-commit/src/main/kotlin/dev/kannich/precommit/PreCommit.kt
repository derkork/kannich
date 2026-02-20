package dev.kannich.precommit

import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.Tool
import dev.kannich.stdlib.fail
import dev.kannich.tools.Cache
import dev.kannich.tools.Fs
import dev.kannich.tools.Shell
import dev.kannich.tools.Web
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Provides pre-commit framework for Kannich pipelines.
 * Downloads and installs the specified pre-commit version on first use.
 *
 * Pre-commit is a framework for managing and maintaining multi-language pre-commit hooks.
 * It can run various checks and formatters on your code before committing:
 * - Code formatting (black, prettier, etc.)
 * - Linting (eslint, flake8, etc.)
 * - Security scanning
 * - And many more hooks
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val preCommit = PreCommit("4.0.1")
 *
 *     val check = job("Pre-commit Check") {
 *         // Run all configured hooks
 *         preCommit.exec("run", "--all-files")
 *
 *         // Run specific hook
 *         preCommit.exec("run", "trailing-whitespace", "--all-files")
 *
 *         // Install hooks
 *         preCommit.exec("install")
 *     }
 * }
 * ```
 */
class PreCommit(val version: String) : Tool {
    private val logger: Logger = LoggerFactory.getLogger(PreCommit::class.java)

    companion object {
        private const val CACHE_KEY = "pre-commit"
    }

    /**
     * Gets the pre-commit installation directory path inside the container.
     */
    suspend fun home(): String =
        Cache.path("$CACHE_KEY/pre-commit-$version")


    override suspend fun getToolPaths() = listOf(home())

    /**
     * Ensures pre-commit is installed in the Cache.
     * Downloads from GitHub releases if not already present.
     */
    override suspend fun ensureInstalled() {
        val cacheKey = "$CACHE_KEY/pre-commit-$version"

        if (Cache.exists(cacheKey)) {
            logger.debug("Pre-commit $version is already installed.")
            return
        }

        logger.info("Pre-commit $version is not installed, downloading.")

        // Ensure pre-commit cache directory exists
        Cache.ensureDir(CACHE_KEY)

        // Create version-specific directory
        val preCommitDir = Cache.path(cacheKey)
        Fs.mkdir(preCommitDir)

        // Download pre-commit .pyz file (Python zip application)
        val downloadUrl = getDownloadUrl(version)
        val downloadedFile = Web.download(downloadUrl)

        // Move the downloaded .pyz file to the pre-commit binary location
        val preCommitBinary = "$preCommitDir/pre-commit"
        Fs.move(downloadedFile, preCommitBinary)

        // Make it executable
        Shell.exec("chmod", "+x", preCommitBinary)

        // Verify installation succeeded - pre-commit binary should exist
        if (!Fs.exists(preCommitBinary)) {
            fail("Pre-commit installation failed: expected binary $preCommitBinary not found")
        }

        logger.info("Successfully installed pre-commit $version.")
    }

    /**
     * Executes pre-commit with the given arguments.
     * Throws JobFailedException if the execution fails.
     *
     * @param args Arguments to pass to pre-commit
     * @throws dev.kannich.stdlib.JobFailedException if the command fails
     */
    suspend fun exec(vararg args: String) {
        ensureInstalled()

        val homeDir = home()
        val preCommitBinary = "$homeDir/pre-commit"

        // Set up cache directory for pre-commit's hooks and environments
        val hooksCacheKey = "$CACHE_KEY/cache"
        Cache.ensureDir(hooksCacheKey)
        val hooksCachePath = Cache.path(hooksCacheKey)

        // Pre-commit uses PRE_COMMIT_HOME environment variable for its cache
        val ctx = JobContext.current()
        val result = ctx.withEnv(mapOf("PRE_COMMIT_HOME" to hooksCachePath)) {
            Shell.exec(preCommitBinary, *args)
        }

        if (!result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("Pre-commit command failed: $errorMessage")
        }
    }

    /**
     * Gets the download URL for the specified pre-commit version.
     * Uses GitHub releases from pre-commit/pre-commit.
     */
    private fun getDownloadUrl(version: String): String {
        // Pre-commit releases are available on GitHub as .pyz files
        // .pyz is a Python zip application - a self-contained executable Python application
        // Format: pre-commit-{version}.pyz
        return "https://github.com/pre-commit/pre-commit/releases/download/v$version/pre-commit-$version.pyz"
    }
}
