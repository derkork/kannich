package dev.kannich.java

import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.fail
import dev.kannich.tools.Cache
import dev.kannich.tools.Compressor
import dev.kannich.tools.Fs
import dev.kannich.tools.Shell
import dev.kannich.tools.Web
import dev.kannich.stdlib.FsKind
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Provides Java SDK management for Kannich pipelines.
 * Downloads and installs the specified Java version on first use.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val java = Java("21")
 *     val compile = job("Compile") {
 *         java.exec("-version")  // prints Java version
 *     }
 * }
 * ```
 */
class Java(val version: String) {
    private val logger: Logger = LoggerFactory.getLogger(Java::class.java)

    companion object {
        private const val CACHE_KEY = "java"
    }

    /**
     * Gets the Java home directory path inside the container.
     */
    suspend fun home(): String =
        Cache.path("$CACHE_KEY/temurin-$version")

    /**
     * Ensures Java is installed in the Cache.
     * Downloads from Adoptium (Eclipse Temurin) if not already present.
     */
    suspend fun ensureInstalled() {
        val cacheKey = "$CACHE_KEY/temurin-$version"

        if (Cache.exists(cacheKey)) {
            logger.debug("Java $version is already installed.")
            return
        }

        logger.info("Java $version is not installed, downloading.")

        // Ensure java cache directory exists
        Cache.ensureDir(CACHE_KEY)

        // Download and extract Java to the java cache directory
        // Using Eclipse Temurin (Adoptium) for reliable downloads
        val downloadUrl = getDownloadUrl(version)
        val javaDir = Cache.path(CACHE_KEY)
        val archive = Web.download(downloadUrl, "java.tar.gz")
        Compressor.extract(archive, javaDir)

        // Adoptium extracts to directories like "jdk-21.0.5+11", rename to our expected name
        val folder = Fs.glob("jdk-${version}*", javaDir, kind = FsKind.Folder).first()
        Fs.move("$javaDir/$folder", Cache.path(cacheKey))

        logger.info("Successfully installed Java $version.")
    }

    /**
     * Executes the Java command with the given arguments.
     * Throws JobFailedException if the execution fails.
     *
     * @param args Arguments to pass to the java command
     * @throws dev.kannich.stdlib.JobFailedException if the command fails
     */
    suspend fun exec(vararg args: String) {
        ensureInstalled()
        val homeDir = home()
        val result = JobContext.current().withEnv(mapOf("JAVA_HOME" to homeDir)) {
            Shell.exec("$homeDir/bin/java", *args)
        }
        if (!result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("Command failed: $errorMessage")
        }
    }

    /**
     * Gets the download URL for the specified Java version.
     * Uses Eclipse Temurin (Adoptium) releases.
     */
    private fun getDownloadUrl(version: String): String {
        // Determine architecture
        val arch = "x64" // TODO: detect architecture
        val os = "linux" // TODO: detect OS

        // Eclipse Temurin download URL pattern
        return "https://api.adoptium.net/v3/binary/latest/$version/ga/$os/$arch/jdk/hotspot/normal/eclipse"
    }
}
