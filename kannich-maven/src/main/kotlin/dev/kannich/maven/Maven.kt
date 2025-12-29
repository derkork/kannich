package dev.kannich.maven

import dev.kannich.jvm.Java
import dev.kannich.stdlib.BaseTool
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
    val logger: Logger = LoggerFactory.getLogger(Maven::class.java)
    /**
     * Gets the Maven home directory path inside the container.
     * The path is computed based on the context's cache directory.
     */
    override fun home(ctx: JobExecutionContext): String =
        "${ctx.pipelineContext.cacheDir}/maven/apache-maven-$version"

    /**
     * Ensures Maven is installed in the cache.
     * Also ensures Java is installed first since Maven depends on it.
     */
    override fun ensureInstalled(ctx: JobExecutionContext) {
        // Ensure Java is installed first
        java.ensureInstalled(ctx)

        val homeDir = home(ctx)
        if (isInstalled(ctx, homeDir)) {
            logger.debug("Maven $version is already installed.")
            return
        }

        logger.info("Maven $version is not installed, downloading.")

        val cacheDir = ctx.pipelineContext.cacheDir
        val mavenDir = "$cacheDir/maven"

        // Create maven directory if needed
        val mkdirResult = ctx.executor.exec(listOf("mkdir", "-p", mavenDir), ctx.workingDir, emptyMap())
        if (!mkdirResult.success) {
            throw RuntimeException("Failed to create cache directory $mavenDir: ${mkdirResult.stderr}")
        }

        // Download and extract Maven
        // Apache Maven tarballs extract to "apache-maven-$version" which matches our home() path
        val downloadUrl = getDownloadUrl(version)
        val extractCmd = "curl -sL \"$downloadUrl\" | tar xzf - -C \"$mavenDir\""

        val extractResult = ctx.executor.exec(listOf("sh", "-c", extractCmd), ctx.workingDir, emptyMap())
        if (!extractResult.success) {
            throw RuntimeException("Failed to download/extract Maven $version: ${extractResult.stderr}")
        }

        // Verify extraction succeeded
        if (!isInstalled(ctx, homeDir)) {
            throw RuntimeException("Maven extraction failed: expected directory $homeDir not found")
        }

        logger.info("Successfully installed Maven $version.")
    }

    override fun doExec(ctx: JobExecutionContext, vararg args: String): ExecResult {
        val homeDir = home(ctx)
        val javaHome = java.home(ctx)
        val cmd = listOf("$homeDir/bin/mvn") + args.toList()
        val env = mapOf(
            "JAVA_HOME" to javaHome,
            "MAVEN_HOME" to homeDir,
            "M2_HOME" to homeDir
        )
        return ctx.executor.exec(cmd, ctx.workingDir, env)
    }

    private fun isInstalled(ctx: JobExecutionContext, homeDir: String): Boolean {
        val result = ctx.executor.exec(listOf("test", "-d", homeDir), ctx.workingDir, emptyMap())
        return result.success
    }

    /**
     * Gets the download URL for the specified Maven version.
     * Uses Apache Maven binary distribution.
     */
    private fun getDownloadUrl(version: String): String {
        return "https://archive.apache.org/dist/maven/maven-3/$version/binaries/apache-maven-$version-bin.tar.gz"
    }
}
