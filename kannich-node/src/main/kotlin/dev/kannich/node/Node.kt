package dev.kannich.node

import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.FsKind
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
 * Provides Node.js for Kannich pipelines.
 * Downloads and installs the specified Node.js version on first use.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val node = Node("22.14.0")
 *     job("Check Node") {
 *         // Use node.home() to get the path to the node installation
 *     }
 * }
 * ```
 */
class Node(val version: String) : Tool {
    private val logger: Logger = LoggerFactory.getLogger(Node::class.java)

    companion object {
        private const val CACHE_KEY = "node"
    }

    val npm = SubTool(this, "npm")
    val npx = SubTool(this, "npx")

    /**
     * Gets the Node.js installation directory path inside the container.
     */
    suspend fun home(): String = Cache.path("$CACHE_KEY/node-$version")

    override suspend fun getToolPaths() = listOf("${home()}/bin")

    /**
     * Ensures Node.js is installed in the Cache.
     * Downloads from nodejs.org if not already present.
     */
    override suspend fun ensureInstalled() {
        val cacheKey = "$CACHE_KEY/node-$version"

        if (Cache.exists(cacheKey)) {
            logger.debug("Node.js $version is already installed.")
            return
        }

        logger.info("Node.js $version is not installed, downloading.")

        // Ensure cache directory exists
        Cache.ensureDir(CACHE_KEY)

        // Create version-specific directory
        val nodeDir = Cache.path(cacheKey)
        Fs.mkdir(nodeDir)

        // Download and extract Node.js.
        // The archive contains a directory like 'node-v22.14.0-linux-x64'
        val downloadUrl = getDownloadUrl(version)
        val archive = Web.download(downloadUrl)
        val tempDir = Fs.mktemp()
        Compressor.extract(archive, tempDir)

        // The archive structure is node-v{version}-linux-x64/bin/node
        val folder = Fs.glob("node-v$version-linux-x64*", tempDir, kind = FsKind.Folder).first()
        Fs.move("$tempDir/$folder", nodeDir)
        Fs.delete(tempDir)
        logger.info("Successfully installed Node.js $version.")
    }

    suspend fun exec(vararg args: String, silent: Boolean = false, allowFailure: Boolean = false) : ExecResult {
       return run("node", *args, silent = silent, allowFailure = allowFailure)
    }

    private suspend fun run(tool: String, vararg args: String, silent: Boolean = false, allowFailure: Boolean = false): ExecResult {
        ensureInstalled()

        val binary = "${home()}/bin/$tool"
        val result = Shell.exec(binary, *args, silent = silent)
        if (!allowFailure && !result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("Command failed: $errorMessage")
        }
        return result
    }


    /**
     * Gets the download URL for the specified Node.js version.
     * Uses nodejs.org.
     */
    private fun getDownloadUrl(version: String): String {
        // Format: https://nodejs.org/dist/v{version}/node-v{version}-linux-x64.tar.xz
        return "https://nodejs.org/dist/v$version/node-v$version-linux-x64.tar.xz"
    }

    class SubTool(private val owner: Node, private val name: String) {
        suspend fun exec(vararg args: String, silent: Boolean = false, allowFailure: Boolean = false): ExecResult {
            // npm and npx expect the node binary to be in the PATH, so we wrap this with
            // a call to withTools to ensure the node binary is available.
            return JobContext.current().withTools(owner) {
                owner.run(name, *args, silent = silent, allowFailure = allowFailure)
            }
        }
    }
}
