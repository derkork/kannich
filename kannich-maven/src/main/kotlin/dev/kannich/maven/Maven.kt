package dev.kannich.maven

import dev.kannich.java.Java
import dev.kannich.stdlib.*
import dev.kannich.tools.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Configuration for a Maven server (used in settings.xml).
 */
data class ServerConfig(
    val id: String,
    val username: String,
    val password: String,
    val headers: Map<String, String>
)

/**
 * DSL builder for configuring a Maven server.
 */
@KannichDsl
class ServerBuilder(private val id: String) {
    var username: String = ""
    var password: String = ""
    private val headers = mutableMapOf<String, String>()

    fun header(name: String, value: String) {
        headers[name] = value
    }

    internal fun build() = ServerConfig(id, username, password, headers)
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
        val builder = ServerBuilder(id)
        builder.block()
        servers.add(builder.build())
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
 *     val ciUsername = getenv("CI_USERNAME") ?: ""
 *     val ciPassword = getenv("CI_PASSWORD") ?: ""
 *     val maven = Maven("3.9.6", java) {
 *         server("ossrh") {
 *             username = ciUsername
 *             password = ciPassword
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
    version: String,
    private val java: Java,
    block: MavenBuilder.() -> Unit = {}
) : ArchiveToolInstaller("maven", version, archiveStripComponents = 1) {
    private val logger: Logger = LoggerFactory.getLogger(Maven::class.java)
    private val config = MavenBuilder().apply(block)
    private val servers = config.servers

    @Deprecated("Use getInstallPath() instead", ReplaceWith("getInstallPath()"), DeprecationLevel.WARNING)
    suspend fun home(): String = getInstallPath()

    override fun getMainExecutable(): String = "bin/mvn"

    override suspend fun ensureInstalled() {
        java.ensureInstalled()
        super.ensureInstalled()
    }

    override suspend fun exec(vararg args: String, silent: Boolean, allowFailure: Boolean) : ExecResult {
        ensureInstalled()

        val homeDir = getInstallPath()
        val javaHome = java.getInstallPath()

        // Build command with settings.xml if servers are configured
        val settingsPath = generateSettingsXml()
        // Register cleanup to delete settings.xml when job completes
        JobContext.current().onCleanup {
            Fs.delete(settingsPath)
        }
        val settingsArgs = mutableListOf("-s", settingsPath)

        // if a kannich-wide settings.xml exists, merge it with the local settings
        if (Fs.exists("/root/.m2/settings.xml")) {
            logger.debug("Using Kannich bootstrap settings.xml.")
            settingsArgs.add("-gs")
            settingsArgs.add("/root/.m2/settings.xml")
        }

        // Cache the downloaded jar files.
        val repositoryCacheKey = "tools/maven/repository"
        Cache.ensureDir(repositoryCacheKey)

        val allArgs = listOf("-Dmaven.repo.local=${Cache.path(repositoryCacheKey)}") +
                settingsArgs + args.toList()

        return JobContext.current().withEnv(
            mapOf(
                "JAVA_HOME" to javaHome,
                "MAVEN_HOME" to homeDir,
                "M2_HOME" to homeDir
            )
        ) {
           super.exec(*allArgs.toTypedArray(), silent = silent, allowFailure = allowFailure)
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
            appendLine(
                """<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">"""
            )

            appendLine("  <servers>")
            for (server in servers) {
                appendLine("    <server>")
                appendLine("      <id>${escapeXml(server.id)}</id>")
                if (server.username.isNotBlank()) {
                    appendLine("      <username>${escapeXml(server.username)}</username>")
                }
                if (server.password.isNotBlank()) {
                    appendLine("      <password>${escapeXml(server.password)}</password>")
                }
                if (server.headers.isNotEmpty()) {
                    appendLine("      <configuration>")
                    for ((key, value) in server.headers) {
                        appendLine("        <httpHeaders>")
                        appendLine("          <property>")
                        appendLine("            <name>${escapeXml(key)}</name>")
                        appendLine("            <value>${escapeXml(value)}</value>")
                        appendLine("          </property>")
                        appendLine("        </httpHeaders>")
                    }
                    appendLine("      </configuration>")
                }
                appendLine("    </server>")
            }
            appendLine("  </servers>")

            appendLine("""  <proxies>""")
            // if there are proxy settings in the environment, put them into settings.xml
            if (System.getProperty("http.proxyHost") != null) {
                appendLine("""    <proxy>""")
                appendLine("""      <id>http_proxy</id>""")
                appendLine("""      <active>true</active>""")
                appendLine("""      <protocol>http</protocol>""")
                appendLine("""      <host>${System.getProperty("http.proxyHost")}</host>""")
                appendLine("""      <port>${System.getProperty("http.proxyPort")}</port>""")
                appendLine("""      <nonProxyHosts>${System.getProperty("http.nonProxyHosts")}</nonProxyHosts>""")
                // append username and password if they are set
                System.getProperty("http.proxyUser")?.let { user ->
                    appendLine("""      <username>$user</username>""")
                }
                System.getProperty("http.proxyPassword")?.let { password ->
                    appendLine("""      <password>$password</password>""")
                }
                appendLine("""    </proxy>""")
            }
            // same for https
            if (System.getProperty("https.proxyHost") != null) {
                appendLine("""    <proxy>""")
                appendLine("""      <id>https_proxy</id>""")
                appendLine("""      <active>true</active>""")
                appendLine("""      <protocol>https</protocol>""")
                appendLine("""      <host>${System.getProperty("https.proxyHost")}</host>""")
                appendLine("""      <port>${System.getProperty("https.proxyPort")}</port>""")
                appendLine("""      <nonProxyHosts>${System.getProperty("https.nonProxyHosts")}</nonProxyHosts>""")
                // append username and password if they are set
                System.getProperty("https.proxyUser")?.let { user ->
                    appendLine("""      <username>$user</username>""")
                }
                System.getProperty("https.proxyPassword")?.let { password ->
                    appendLine("""      <password>$password</password>""")
                }
                appendLine("""    </proxy>""")
            }

            appendLine("""  </proxies>""")
            appendLine("</settings>")
        }

        Fs.write(settingsPath, xml)
        return settingsPath
    }

    /**
     * Returns the version of the Maven project in the current working directory.
     */
    suspend fun getProjectVersion(): String {
        return evaluateExpression("project.version")
    }

    /**
     * Evaluates an expression in the current Maven project.
     */
    suspend fun evaluateExpression(expression: String): String {
        val tempDir = Fs.mktemp()
        exec("help:evaluate", "-Dexpression=$expression", "-q", "-Doutput=$tempDir/result.txt", silent = true)
        return Fs.readAsString("$tempDir/result.txt")
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

    override fun getDownloadUrl(): String {
        return "https://archive.apache.org/dist/maven/maven-3/$version/binaries/apache-maven-$version-bin.tar.gz"
    }
}
