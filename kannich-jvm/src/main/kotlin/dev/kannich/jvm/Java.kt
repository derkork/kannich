package dev.kannich.jvm

import dev.kannich.stdlib.BaseTool
import dev.kannich.stdlib.CacheTool
import dev.kannich.stdlib.ExtractTool
import dev.kannich.stdlib.FsTool
import dev.kannich.stdlib.context.ExecResult
import dev.kannich.stdlib.context.JobExecutionContext
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
class Java(version: String) : BaseTool(version) {
    private val logger: Logger = LoggerFactory.getLogger(Java::class.java)
    private val cache = CacheTool()
    private val extract = ExtractTool()
    private val fs = FsTool()

    companion object {
        private const val CACHE_KEY = "java"
    }

    /**
     * Gets the Java home directory path inside the container.
     * The path is computed based on the context's cache directory.
     */
    override fun home(ctx: JobExecutionContext): String =
        cache.path("$CACHE_KEY/temurin-$version")

    /**
     * Ensures Java is installed in the cache.
     * Downloads from Adoptium (Eclipse Temurin) if not already present.
     */
    override fun ensureInstalled(ctx: JobExecutionContext) {
        val cacheKey = "$CACHE_KEY/temurin-$version"

        if (cache.exists(cacheKey)) {
            logger.debug("Java $version is already installed.")
            return
        }

        logger.info("Java $version is not installed, downloading.")

        // Ensure java cache directory exists
        cache.ensureDir(CACHE_KEY)

        // Download and extract Java to the java cache directory
        // Using Eclipse Temurin (Adoptium) for reliable downloads
        val downloadUrl = getDownloadUrl(version)
        val javaDir = cache.path(CACHE_KEY)
        extract.downloadAndExtract(downloadUrl, javaDir)

        // Adoptium extracts to directories like "jdk-21.0.5+11", rename to our expected name
        fs.move("$javaDir/jdk-${version}*", cache.path(cacheKey))

        logger.info("Successfully installed Java $version.")
    }

    override fun doExec(ctx: JobExecutionContext, vararg args: String): ExecResult {
        val homeDir = home(ctx)
        val cmd = listOf("$homeDir/bin/java") + args.toList()
        val env = mapOf("JAVA_HOME" to homeDir)
        return ctx.executor.exec(cmd, ctx.workingDir, env, false)
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
