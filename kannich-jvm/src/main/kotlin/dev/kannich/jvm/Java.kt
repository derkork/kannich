package dev.kannich.jvm

import dev.kannich.stdlib.BaseTool
import dev.kannich.stdlib.context.ExecResult
import dev.kannich.stdlib.context.JobExecutionContext

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

    /**
     * Gets the Java home directory path inside the container.
     * The path is computed based on the context's cache directory.
     */
    override fun home(ctx: JobExecutionContext): String =
        "${ctx.pipelineContext.cacheDir}/java/temurin-$version"

    /**
     * Ensures Java is installed in the cache.
     * Downloads from Adoptium (Eclipse Temurin) if not already present.
     */
    override fun ensureInstalled(ctx: JobExecutionContext) {
        val homeDir = home(ctx)
        if (isInstalled(ctx, homeDir)) {
            return
        }

        val cacheDir = ctx.pipelineContext.cacheDir
        val javaDir = "$cacheDir/java"

        // Create java directory if needed
        ctx.executor.exec(listOf("mkdir", "-p", javaDir), ctx.workingDir, emptyMap())

        // Download and extract Java
        // Using Eclipse Temurin (Adoptium) for reliable downloads
        val downloadUrl = getDownloadUrl(version)
        val extractCmd = """
            curl -sL "$downloadUrl" | tar xzf - -C "$javaDir" &&
            mv "$javaDir"/temurin-*-jdk-* "$homeDir" 2>/dev/null || true
        """.trimIndent().replace("\n", " ")

        val result = ctx.executor.exec(listOf("sh", "-c", extractCmd), ctx.workingDir, emptyMap())
        if (!result.success) {
            throw RuntimeException("Failed to download Java $version: ${result.stderr}")
        }
    }

    override fun doExec(ctx: JobExecutionContext, vararg args: String): ExecResult {
        val homeDir = home(ctx)
        val cmd = listOf("$homeDir/bin/java") + args.toList()
        val env = mapOf("JAVA_HOME" to homeDir)
        return ctx.executor.exec(cmd, ctx.workingDir, env)
    }

    private fun isInstalled(ctx: JobExecutionContext, homeDir: String): Boolean {
        val result = ctx.executor.exec(listOf("test", "-d", homeDir), ctx.workingDir, emptyMap())
        return result.success
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
