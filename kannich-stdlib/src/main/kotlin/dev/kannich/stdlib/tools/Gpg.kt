package dev.kannich.stdlib.tools

import dev.kannich.stdlib.context.currentJobContext
import dev.kannich.stdlib.fail
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
 *
 *     val sign = job("Sign") {
 *         // Import key from environment variable
 *         Gpg.importKey(getenv("GPG_PRIVATE_KEY") ?: "")
 *
 *         // Or import from a file
 *         Gpg.importKeyFile("/workspace/keys/signing-key.asc")
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
    suspend fun importKey(key: String) {
        if (key.isBlank()) {
            fail("GPG key cannot be empty")
        }

        val ctx = currentJobContext()
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
    suspend fun importKeyFile(path: String, deleteAfterImport: Boolean = false) {
        if (!Fs.exists(path)) {
            fail("GPG key file not found: $path")
        }

        try {
            doImport(path)
        } finally {
            if (deleteAfterImport && Fs.exists(path)) {
                Fs.delete(path)
                logger.debug("Deleted key file after import: $path")
            }
        }
    }

    /**
     * Performs the actual GPG import operation.
     */
    private suspend fun doImport(keyPath: String) {
        logger.info("Importing GPG key from: $keyPath")

        // Import the key using gpg
        val result = Shell.execShell("gpg --batch --import '$keyPath' 2>&1")

        if (!result.success) {
            fail("Failed to import GPG key: ${result.stdout}")
        }

        // Log success - gpg outputs to stderr even on success
        logger.info("Successfully imported GPG key")
    }
}