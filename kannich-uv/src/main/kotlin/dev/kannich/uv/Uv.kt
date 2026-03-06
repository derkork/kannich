package dev.kannich.uv

import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.JobContext
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
 * Provides UV for Kannich pipelines.
 * Downloads and installs the specified UV version on first use.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val uv = Uv("0.10.7")
 *     job("Check UV") {
 *         uv.exec("--version")
 *     }
 * }
 * ```
 */
class Uv(val version: String) : Tool {
    private val logger: Logger = LoggerFactory.getLogger(Uv::class.java)

    companion object {
        private const val CACHE_KEY = "uv"
    }

    /**
     * Gets the UV installation directory path inside the container.
     */
    suspend fun home(): String = Cache.path("$CACHE_KEY/uv-$version")

    override suspend fun getToolPaths() = listOf(home())

    /**
     * Ensures UV is installed in the Cache.
     * Downloads from GitHub if not already present.
     */
    override suspend fun ensureInstalled() {
        val cacheKey = "$CACHE_KEY/uv-$version"

        if (Cache.exists(cacheKey)) {
            logger.debug("UV $version is already installed.")
            return
        }

        logger.info("UV $version is not installed, downloading.")

        // Ensure cache directory exists
        Cache.ensureDir(CACHE_KEY)

        // Create version-specific directory
        val uvDir = Cache.path(cacheKey)
        Fs.mkdir(uvDir)

        // Download and extract UV. 
        // The archive contains a directory like 'uv-x86_64-unknown-linux-musl' which contains the 'uv' binary.
        val downloadUrl = getDownloadUrl(version)
        val archive = Web.download(downloadUrl)
        Compressor.extract(archive, uvDir)

        // Find the uv binary in the extracted directory
        // The archive structure is uv-v{version}-{platform}/uv
        val uvBinary = "$uvDir/uv-x86_64-unknown-linux-musl/uv"
        if (!Fs.exists(uvBinary)) {
            // Fallback: search for uv binary if the directory name differs
            fail("UV extraction failed: expected binary $uvBinary not found")
        }

        // Move the binary to the root of the uvDir to simplify getToolPaths
        Shell.exec("mv", uvBinary, "$uvDir/uv")
        
        // Set executable bit just in case
        Shell.exec("chmod", "+x", "$uvDir/uv")

        logger.info("Successfully installed UV $version.")
    }

    /**
     * Executes UV with the given arguments.
     *
     * @param args Arguments to pass to `uv`
     */
    override suspend fun exec(vararg args: String, silent: Boolean, allowFailure: Boolean) : ExecResult {
        ensureInstalled()

        val uvBinary = "${home()}/uv"

        // cache for package and wheel downloads
        val uvCacheKey = "$CACHE_KEY/uv-cache"
        Cache.ensureDir(uvCacheKey)
        val uvCachePath = Cache.path(uvCacheKey)

        // cache for python installations
        val uvPythonInstallKey = "$CACHE_KEY/uv-pythons"
        Cache.ensureDir(uvPythonInstallKey)
        val uvPythonInstallPath = Cache.path(uvPythonInstallKey)

        val result = JobContext.current().withEnv(mapOf(
            "UV_CACHE_DIR" to uvCachePath,
            "UV_PYTHON_INSTALL_DIR" to uvPythonInstallPath,
            "UV_LINK_MODE" to "copy",  // use copy because we work on an overlayfs and hardlinks won't work there.
        )) {
           Shell.exec(uvBinary, *args, silent = silent)
        }

        if (!allowFailure && !result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code:${result.exitCode}" }
            fail("UV command failed: $errorMessage")
        }

        return result
    }

    /**
     * Gets the download URL for the specified UV version.
     * Uses github.com.
     */
    private fun getDownloadUrl(version: String): String {
        // Format: https://github.com/astral-sh/uv/releases/download/{version}/uv-x86_64-unknown-linux-musl.tar.gz
        return "https://github.com/astral-sh/uv/releases/download/${version}/uv-x86_64-unknown-linux-musl.tar.gz"
    }
}
