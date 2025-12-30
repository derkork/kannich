package dev.kannich.maven

import dev.kannich.jvm.Java
import dev.kannich.stdlib.BaseTool
import dev.kannich.stdlib.tools.CacheTool
import dev.kannich.stdlib.tools.ExtractTool
import dev.kannich.stdlib.context.ExecResult
import dev.kannich.stdlib.context.JobExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Provides Maven build support for Kannich pipelines.
 * Downloads and installs the specified Maven version on first use.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val java = Java("21")
 *     val maven = Maven("3.9.6", java)
 *
 *     val build = job("Build") {
 *         maven.exec("clean", "package")
 *     }
 * }
 * ```
 */
class Maven(version: String, private val java: Java) : BaseTool(version) {
    private val logger: Logger = LoggerFactory.getLogger(Maven::class.java)
    private val cache = CacheTool()
    private val extract = ExtractTool()

    companion object {
        private const val CACHE_KEY = "maven"
    }

    /**
     * Gets the Maven home directory path inside the container.
     * The path is computed based on the context's cache directory.
     */
    override fun home(ctx: JobExecutionContext): String =
        cache.path("$CACHE_KEY/apache-maven-$version")

    /**
     * Ensures Maven is installed in the cache.
     * Also ensures Java is installed first since Maven depends on it.
     */
    override fun ensureInstalled(ctx: JobExecutionContext) {
        // Ensure Java is installed first
        java.ensureInstalled(ctx)

        val cacheKey = "$CACHE_KEY/apache-maven-$version"

        if (cache.exists(cacheKey)) {
            logger.debug("Maven $version is already installed.")
            return
        }

        logger.info("Maven $version is not installed, downloading.")

        // Ensure maven cache directory exists
        cache.ensureDir(CACHE_KEY)

        // Download and extract Maven
        // Apache Maven tarballs extract to "apache-maven-$version" which matches our cache key
        val downloadUrl = getDownloadUrl(version)
        val mavenDir = cache.path(CACHE_KEY)
        extract.downloadAndExtract(downloadUrl, mavenDir)

        // Verify extraction succeeded
        if (!cache.exists(cacheKey)) {
            throw RuntimeException("Maven extraction failed: expected directory ${cache.path(cacheKey)} not found")
        }

        logger.info("Successfully installed Maven $version.")
    }

    override fun doExec(ctx: JobExecutionContext, vararg args: String): ExecResult {
        val homeDir = home(ctx)
        val javaHome = java.home(ctx)
        val env = mapOf(
            "JAVA_HOME" to javaHome,
            "MAVEN_HOME" to homeDir,
            "M2_HOME" to homeDir
        )

        // Cache the downloaded jar files.
        val repositoryCacheKey = "$CACHE_KEY/repository"
        cache.ensureDir(repositoryCacheKey)

        val cmd = listOf("$homeDir/bin/mvn", "-Dmaven.repo.local=${cache.path(repositoryCacheKey)}") + args.toList()
        return ctx.executor.exec(cmd, ctx.workingDir, env, false)
    }

    /**
     * Gets the download URL for the specified Maven version.
     * Uses Apache Maven binary distribution.
     */
    private fun getDownloadUrl(version: String): String {
        return "https://archive.apache.org/dist/maven/maven-3/$version/binaries/apache-maven-$version-bin.tar.gz"
    }
}
