package dev.kannich.maven

import dev.kannich.java.Java
import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.KannichDsl
import dev.kannich.stdlib.fail
import dev.kannich.tools.Compressor
import dev.kannich.tools.Shell
import dev.kannich.tools.Cache
import dev.kannich.tools.Fs
import dev.kannich.tools.Web
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
    private val logger: Logger = LoggerFactory.getLogger(Maven::class.java)
    private val config = MavenBuilder().apply(block)
    private val servers = config.servers


    companion object {
        private const val CACHE_KEY = "maven"
    }

    /**
     * Gets the Maven home directory path inside the container.
     */
    suspend fun home(): String =
        Cache.path("$CACHE_KEY/apache-maven-$version")

    /**
     * Ensures Maven is installed in the Cache.
     * Also ensures Java is installed first since Maven depends on it.
     */
    suspend fun ensureInstalled() {
        // Ensure Java is installed first
        java.ensureInstalled()

        val cacheKey = "$CACHE_KEY/apache-maven-$version"

        if (Cache.exists(cacheKey)) {
            logger.debug("Maven $version is already installed.")
            return
        }

        logger.info("Maven $version is not installed, downloading.")

        // Ensure maven cache directory exists
        Cache.ensureDir(CACHE_KEY)

        // Download and extract Maven
        // Apache Maven tarballs extract to "apache-maven-$version" which matches our cache key
        val downloadUrl = getDownloadUrl(version)
        val mavenDir = Cache.path(CACHE_KEY)
        val archive = Web.download(downloadUrl, "maven.tar.gz")
        Compressor.extract(archive, mavenDir)

        // Verify extraction succeeded
        if (!Cache.exists(cacheKey)) {
            throw RuntimeException("Maven extraction failed: expected directory ${Cache.path(cacheKey)} not found")
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
    suspend fun exec(vararg args: String) {
        ensureInstalled()

        val homeDir = home()
        val javaHome = java.home()

        // Build command with settings.xml if servers are configured
        val settingsArgs = if (servers.isNotEmpty()) {
            val settingsPath = generateSettingsXml()
            // Register cleanup to delete settings.xml when job completes
            JobContext.current().onCleanup {
                Fs.delete(settingsPath)
            }
            listOf("-s", settingsPath)
        } else {
            emptyList()
        }

        // Cache the downloaded jar files.
        val repositoryCacheKey = "$CACHE_KEY/repository"
        Cache.ensureDir(repositoryCacheKey)

        val allArgs = listOf("-Dmaven.repo.local=${Cache.path(repositoryCacheKey)}") +
                      settingsArgs + args.toList()

        val env = mapOf(
            "JAVA_HOME" to javaHome,
            "MAVEN_HOME" to homeDir,
            "M2_HOME" to homeDir
        )

        val result = JobContext.current().withEnv(env) {
            Shell.exec("$homeDir/bin/mvn", *allArgs.toTypedArray())
        }

        if (!result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("Command failed: $errorMessage")
        }
    }

    /**
     * Generates a settings.xml file with server credentials.
     * Returns the path to the generated file.
     */
    private suspend fun generateSettingsXml(): String {
        val ctx = JobContext.current()
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

        Fs.write(settingsPath, xml)
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
