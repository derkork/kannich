package dev.kannich.tools

import dev.kannich.stdlib.Arch
import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.Tool
import dev.kannich.stdlib.fail
import org.slf4j.LoggerFactory

/**
 * Base class for tool installers that download an archive and extract it.
 */
abstract class ArchiveToolInstaller(
    val name: String,
    val version: String,
    private val archiveStripComponents: Int = 0,
    private val archiveFormat: String = ".tar.gz"
) : Tool {
    private val logger = LoggerFactory.getLogger(ArchiveToolInstaller::class.java)

    /**
     * The cache key for the tool installation.
     */
    private val cacheKey = "tools/$name/${Arch.current.archString}/${version}"

    /**
     * Gets the installation path for the tool.
     */
    suspend fun getInstallPath() = Cache.path(cacheKey)

    /**
     * Returns the path to the main executable inside the archive relative to the extracted root (e.g. bin/java).
     */
    abstract fun getMainExecutable(): String

    /**
     * Returns the download URL for the tool in the current version and the current architecture.
     */
    abstract fun getDownloadUrl(): String


    override suspend fun ensureInstalled() {
        val root = getInstallPath()
        val executable = root + "/" + getMainExecutable()

        if (Fs.exists(executable)) {
            logger.debug("$name $version is already installed.")
            return
        }

        val archive = Web.download(getDownloadUrl(), "tool$archiveFormat")
        Compressor.extract(archive, root, stripComponents = archiveStripComponents)

        // Verify extraction succeeded. We should have the executable at the expected path.
        if (!Fs.exists(executable)) {
            fail("$name extraction failed: expected executable $executable not found")
        }

        // Just to be sure, set the permissions, as ZIP archives don't preserve them
        Fs.chmod(executable, "755")
        logger.info("Successfully installed $name $version.")
    }


    override suspend fun getToolPaths(): List<String> =
        listOf((Cache.path(cacheKey) + "/" + getMainExecutable()).substringBeforeLast("/"))

    override suspend fun exec(
        vararg args: String,
        silent: Boolean,
        allowFailure: Boolean
    ): ExecResult = execWithExecutable(getMainExecutable(), *args, silent = silent, allowFailure = allowFailure)

    protected suspend fun execWithExecutable(
        executable: String, vararg args: String,
        silent: Boolean,
        allowFailure: Boolean
    ): ExecResult {
        ensureInstalled()

        val fullExecutablePath = Cache.path(cacheKey) + "/" + executable
        val result = Shell.exec(fullExecutablePath, *args, silent = silent)

        if (!allowFailure && !result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("command failed: $errorMessage")
        }

        return result
    }
}