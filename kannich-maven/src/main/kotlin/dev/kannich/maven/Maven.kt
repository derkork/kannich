package dev.kannich.maven

import dev.kannich.jvm.Java
import dev.kannich.stdlib.JobScope
import dev.kannich.stdlib.KannichDsl
import dev.kannich.stdlib.fail
import dev.kannich.stdlib.tools.CacheTool
import dev.kannich.stdlib.tools.ExtractTool
import dev.kannich.stdlib.tools.FsTool
import dev.kannich.stdlib.tools.ShellTool
import dev.kannich.stdlib.context.JobExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Configuration for a Maven server (used in settings.xml).
 */
data class ServerConfig(
    val id: String,
    val username: String,
    val password: String
)

/**
 * DSL builder for configuring a Maven server.
 */
@KannichDsl
class ServerBuilder(private val id: String) {
    var username: String = ""
    var password: String = ""

    internal fun build() = ServerConfig(id, username, password)
}

/**
 * DSL builder for configuring Maven.
 */
@KannichDsl
class MavenBuilder {
    internal val servers = mutableListOf<ServerConfig>()

    /**
     * Configures a server for authentication.
     * The server id should match the id in your pom.xml distributionManagement section.
     *
     * @param id The server id
     * @param block Configuration block for the server
     */
    fun server(id: String, block: ServerBuilder.() -> Unit) {
        servers.add(ServerBuilder(id).apply(block).build())
    }
}

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
 *
 * With server configuration:
 * ```kotlin
 * pipeline {
 *     val java = Java("21")
 *     val maven = Maven("3.9.6", java) {
 *         server("ossrh") {
 *             username = getenv("CI_USERNAME") ?: ""
 *             password = getenv("CI_PASSWORD") ?: ""
 *         }
 *     }
 *
 *     val deploy = job("Deploy") {
 *         maven.exec("deploy")
 *     }
 * }
 * ```
 */
class Maven(
    val version: String,
    private val java: Java,
    block: MavenBuilder.() -> Unit = {}
) {
    private val config = MavenBuilder().apply(block)
    private val servers = config.servers

    private val logger: Logger = LoggerFactory.getLogger(Maven::class.java)
    private val cache = CacheTool()
    private val extract = ExtractTool()
    private val fs = FsTool()
    private val shell = ShellTool()

    companion object {
        private const val CACHE_KEY = "maven"
    }

    /**
     * Gets the Maven home directory path inside the container.
     */
    fun home(): String =
        cache.path("$CACHE_KEY/apache-maven-$version")

    /**
     * Ensures Maven is installed in the cache.
     * Also ensures Java is installed first since Maven depends on it.
     */
    fun ensureInstalled() {
        // Ensure Java is installed first
        java.ensureInstalled()

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

    /**
     * Executes Maven with the given arguments.
     * Throws JobFailedException if the execution fails.
     *
     * @param args Arguments to pass to Maven
     * @throws dev.kannich.stdlib.JobFailedException if the command fails
     */
    fun exec(vararg args: String) {
        ensureInstalled()

        val homeDir = home()
        val javaHome = java.home()
        val env = mapOf(
            "JAVA_HOME" to javaHome,
            "MAVEN_HOME" to homeDir,
            "M2_HOME" to homeDir
        )

        // Build command with settings.xml if servers are configured
        val settingsArgs = if (servers.isNotEmpty()) {
            val settingsPath = generateSettingsXml()
            // Register cleanup to delete settings.xml when job completes
            JobScope.current().onCleanup {
                fs.delete(settingsPath)
            }
            listOf("-s", settingsPath)
        } else {
            emptyList()
        }

        // Cache the downloaded jar files.
        val repositoryCacheKey = "$CACHE_KEY/repository"
        cache.ensureDir(repositoryCacheKey)

        val allArgs = listOf("-Dmaven.repo.local=${cache.path(repositoryCacheKey)}") +
                      settingsArgs + args.toList()
        val result = shell.exec("$homeDir/bin/mvn", *allArgs.toTypedArray(), env = env)
        if (!result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("Command failed: $errorMessage")
        }
    }

    /**
     * Generates a settings.xml file with server credentials.
     * Returns the path to the generated file.
     */
    private fun generateSettingsXml(): String {
        val ctx = JobExecutionContext.current()
        val settingsPath = "${ctx.workingDir}/.kannich/settings.xml"

        val xml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">""")
            appendLine("  <servers>")
            for (server in servers) {
                appendLine("    <server>")
                appendLine("      <id>${escapeXml(server.id)}</id>")
                appendLine("      <username>${escapeXml(server.username)}</username>")
                appendLine("      <password>${escapeXml(server.password)}</password>")
                appendLine("    </server>")
            }
            appendLine("  </servers>")
            appendLine("</settings>")
        }

        fs.write(settingsPath, xml)
        return settingsPath
    }

    /**
     * Escapes special XML characters.
     */
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Gets the download URL for the specified Maven version.
     * Uses Apache Maven binary distribution.
     */
    private fun getDownloadUrl(version: String): String {
        return "https://archive.apache.org/dist/maven/maven-3/$version/binaries/apache-maven-$version-bin.tar.gz"
    }
}
