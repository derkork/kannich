package dev.kannich.gpg

import dev.kannich.stdlib.fail
import dev.kannich.stdlib.tools.Shell
import dev.kannich.stdlib.context.JobExecutionContext
import dev.kannich.stdlib.tools.Fs
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Provides GPG key management for Kannich pipelines.
 * Used for code signing operations with Maven or other tools.
 *
 * This tool uses the system-installed gpg2 (gnupg2) which is included
 * in the Kannich builder image.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val gpg = Gpg()
 *
 *     val sign = job("Sign") {
 *         // Import key from environment variable
 *         gpg.importKey(getenv("GPG_PRIVATE_KEY") ?: "")
 *
 *         // Or import from a file
 *         gpg.importKeyFile("/workspace/keys/signing-key.asc")
 *
 *         // Now Maven can use gpg for signing
 *         maven.exec("deploy", "-Dgpg.passphrase=${getenv("GPG_PASSPHRASE")}")
 *     }
 * }
 * ```
 */
object Gpg {
    private val logger: Logger = LoggerFactory.getLogger(Gpg::class.java)

    /**
     * Imports a GPG key from a string.
     * The key is written to a temporary file, imported, and then deleted.
     *
     * This is useful for importing keys stored in environment variables
     * or secrets managers.
     *
     * @param key The GPG key in ASCII-armored format (including -----BEGIN PGP... headers)
     * @throws dev.kannich.stdlib.JobFailedException if import fails
     */
    fun importKey(key: String) {
        if (key.isBlank()) {
            fail("GPG key cannot be empty")
        }

        val ctx = JobExecutionContext.current()
        val tempKeyFile = "${ctx.workingDir}/.kannich/gpg-key-${System.currentTimeMillis()}.asc"

        try {
            // Write key to temporary file
            Fs.write(tempKeyFile, key)
            logger.debug("Wrote GPG key to temporary file: $tempKeyFile")

            // Import the key
            doImport(tempKeyFile)
        } finally {
            // Always delete the temporary key file
            if (Fs.exists(tempKeyFile)) {
                Fs.delete(tempKeyFile)
                logger.debug("Deleted temporary key file: $tempKeyFile")
            }
        }
    }

    /**
     * Imports a GPG key from a file in the workspace.
     *
     * @param path The path to the key file (relative to workspace or absolute)
     * @param deleteAfterImport Whether to delete the key file after import (default: false)
     * @throws dev.kannich.stdlib.JobFailedException if import fails or file doesn't exist
     */
    fun importKeyFile(path: String, deleteAfterImport: Boolean = false) {
        val ctx = JobExecutionContext.current()

        // Resolve path relative to workspace if not absolute
        val keyPath = if (path.startsWith("/")) {
            path
        } else {
            "${ctx.workingDir}/$path"
        }

        if (!Fs.exists(keyPath)) {
            fail("GPG key file not found: $keyPath")
        }

        try {
            doImport(keyPath)
        } finally {
            if (deleteAfterImport && Fs.exists(keyPath)) {
                Fs.delete(keyPath)
                logger.info("Deleted key file after import: $keyPath")
            }
        }
    }

    /**
     * Performs the actual GPG import operation.
     */
    private fun doImport(keyPath: String) {
        logger.info("Importing GPG key from: $keyPath")

        // Import the key using gpg
        val result = Shell.execShell("gpg --batch --import '$keyPath' 2>&1")

        if (!result.success) {
            fail("Failed to import GPG key: ${result.stdout}")
        }

        // Log success - gpg outputs to stderr even on success
        logger.info("Successfully imported GPG key")

        // Optionally list the imported key for verification
        val listResult = Shell.execShell("gpg --list-secret-keys --keyid-format LONG 2>&1")
        if (listResult.success && listResult.stdout.isNotBlank()) {
            logger.debug("Available secret keys:\n${listResult.stdout}")
        }
    }
}
