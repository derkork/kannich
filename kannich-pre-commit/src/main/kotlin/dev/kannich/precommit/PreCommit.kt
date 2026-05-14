package dev.kannich.precommit

import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.Tool
import dev.kannich.stdlib.fail
import dev.kannich.stdlib.Arch
import dev.kannich.tools.Cache
import dev.kannich.tools.Fs
import dev.kannich.tools.Shell
import dev.kannich.tools.Web
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
    private val logger = LoggerFactory.getLogger(PreCommit::class.java)

    private val installDir = "tools/pre-commit/${Arch.current.archString}/$version"
    private val binary = "$installDir/pre-commit"

    override suspend fun getToolPaths() = listOf(Cache.path(installDir))

    override suspend fun ensureInstalled() {
        if (Fs.exists(Cache.path(binary))) {
            logger.debug("Pre-commit $version is already installed.")
            return
        }

        logger.info("Pre-commit $version is not installed, downloading.")

        Cache.ensureDir(installDir)

        // pre-commit ships as a .pyz (Python zip application) - a self-contained executable
        val downloadedFile = Web.download(
            "https://github.com/pre-commit/pre-commit/releases/download/v$version/pre-commit-$version.pyz"
        )
        Fs.move(downloadedFile, Cache.path(binary))
        Fs.chmod(Cache.path(binary), "755")

        if (!Fs.exists(Cache.path(binary))) {
            fail("Pre-commit installation failed: binary not found after download")
        }

        logger.info("Successfully installed pre-commit $version.")
    }

    override suspend fun exec(vararg args: String, silent: Boolean, allowFailure: Boolean): ExecResult {
        ensureInstalled()

        // Pre-commit uses PRE_COMMIT_HOME for its hooks and environments cache
        val hooksCacheKey = "tools/pre-commit/cache"
        Cache.ensureDir(hooksCacheKey)

        val result = JobContext.current().withEnv(mapOf("PRE_COMMIT_HOME" to Cache.path(hooksCacheKey))) {
            Shell.exec(Cache.path(binary), *args, silent = silent)
        }

        if (!allowFailure && !result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("Pre-commit command failed: $errorMessage")
        }

        return result
    }
}
