package dev.kannich.sops

import dev.kannich.stdlib.Arch
import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.Tool
import dev.kannich.stdlib.fail
import dev.kannich.tools.Cache
import dev.kannich.tools.Fs
import dev.kannich.tools.Shell
import dev.kannich.tools.Web
import org.slf4j.LoggerFactory

/**
 * Provides SOPS (Secrets OPerationS) for Kannich pipelines.
 * Downloads and installs the specified SOPS version on first use.
 *
 * SOPS is an editor of encrypted files that supports YAML, JSON, ENV, INI and
 * BINARY formats and encrypts with age, PGP, AWS KMS, GCP KMS, Azure Key Vault
 * and HashiCorp Vault.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val sops = Sops("3.13.3")
 *
 *     val decrypt = job("Decrypt secrets") {
 *         sops.exec("-d", "secrets.enc.yaml")
 *     }
 * }
 * ```
 */
class Sops(val version: String) : Tool {
    private val logger = LoggerFactory.getLogger(Sops::class.java)

    private val installDir = "tools/sops/${Arch.current.archString}/$version"
    private val binary = "$installDir/sops"

    override suspend fun getToolPaths() = listOf(Cache.path(installDir))

    override suspend fun ensureInstalled() {
        if (Fs.exists(Cache.path(binary))) {
            logger.debug("sops $version is already installed.")
            return
        }

        logger.info("sops $version is not installed, downloading.")

        val arch = when (val current = Arch.current) {
            is Arch.Amd64 -> "amd64"
            is Arch.Arm64 -> "arm64"
            is Arch.Unknown -> fail("Unsupported architecture: ${current.archString}")
        }

        Cache.ensureDir(installDir)

        // sops ships as a bare binary on Linux, there is no archive to extract.
        val downloadedFile = Web.download(
            "https://github.com/getsops/sops/releases/download/v$version/sops-v$version.linux.$arch"
        )
        Fs.move(downloadedFile, Cache.path(binary))
        Fs.chmod(Cache.path(binary), "755")

        if (!Fs.exists(Cache.path(binary))) {
            fail("sops installation failed: binary not found after download")
        }

        logger.info("Successfully installed sops $version.")
    }

    override suspend fun exec(vararg args: String, silent: Boolean, allowFailure: Boolean): ExecResult {
        ensureInstalled()

        val result = Shell.exec(Cache.path(binary), *args, silent = silent)

        if (!allowFailure && !result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("command failed: $errorMessage")
        }

        return result
    }
}
