package dev.kannich.node

import dev.kannich.stdlib.Arch
import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.fail
import dev.kannich.tools.ArchiveToolInstaller
import dev.kannich.tools.Fs
import dev.kannich.tools.Shell

/**
 * Provides Node.js for Kannich pipelines.
 * Downloads and installs the specified Node.js version on first use.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val node = Node("22.14.0")
 *     job("Check Node") {
 *         // Use node.getInstallPath() to get the path to the node installation
 *     }
 * }
 * ```
 */
class Node(version: String) : ArchiveToolInstaller("node", version, archiveStripComponents = 1, archiveFormat = ".tar.xz") {

    val npm = SubTool(this, "npm")
    val npx = SubTool(this, "npx")

    @Deprecated("Use getInstallPath() instead", ReplaceWith("getInstallPath()"), DeprecationLevel.WARNING)
    suspend fun home(): String = getInstallPath()

    override fun getMainExecutable(): String = "bin/node"


    override suspend fun ensureInstalled() {
        super.ensureInstalled()
        // ensure npx and npm are marked executable
        Fs.chmod(getInstallPath() + "/bin/npx", "755")
        Fs.chmod(getInstallPath() + "/bin/npm", "755")
    }

    override fun getDownloadUrl(): String {
        val arch = when (val current = Arch.current) {
            is Arch.Amd64 -> "x64"
            is Arch.Arm64 -> "arm64"
            is Arch.Unknown -> fail("Unsupported architecture: ${current.archString}")
        }
        return "https://nodejs.org/dist/v$version/node-v$version-linux-$arch.tar.xz"
    }

    class SubTool(private val owner: Node, private val name: String) {
        suspend fun exec(vararg args: String, silent: Boolean = false, allowFailure: Boolean = false): ExecResult {
            // npm and npx expect the node binary to be in the PATH, so we wrap this with
            // a call to withTools to ensure the node binary is available.
            return JobContext.current().withTools(owner) {
                owner.execWithExecutable("bin/$name", *args, silent = silent, allowFailure = allowFailure)
            }
        }
    }
}
