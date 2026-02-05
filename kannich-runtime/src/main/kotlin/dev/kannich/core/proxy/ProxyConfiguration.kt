package dev.kannich.core.proxy

import dev.kannich.stdlib.SecretRegistry
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * Configures HTTP/HTTPS proxy settings for Maven dependency resolution.
 *
 * This utility parses standard proxy environment variables (http_proxy, https_proxy, no_proxy)
 * and configures Java system properties that Maven Resolver's HttpTransporterFactory respects.
 * Both lowercase and uppercase variants are supported, with lowercase taking precedence.
 *
 * Why system properties instead of configuring MavenDependenciesResolver directly:
 * - The Kotlin scripting API doesn't expose proxy configuration on MavenDependenciesResolver
 * - Maven Resolver's HttpTransporterFactory automatically reads these system properties
 * - This is the standard mechanism used by the Maven ecosystem
 *
 * Why this happens before script compilation:
 * - Maven dependency resolution occurs during script compilation (@file:DependsOn processing)
 * - Proxies must be configured before the script is evaluated
 * - Uses the same environment map that will be passed to job execution
 */
object ProxyConfiguration {
    private val logger = LoggerFactory.getLogger(ProxyConfiguration::class.java)

    /**
     * Configures proxy settings from environment variables.
     * Checks both uppercase and lowercase variants (lowercase takes precedence).
     *
     * @param env Environment variables map (from host + CLI args)
     */
    fun configureFromEnvironment(env: Map<String, String>) {
        logger.debug("Configuring proxy from environment")

        // Parse HTTP proxy (lowercase takes precedence)
        val httpProxyUrl = env["http_proxy"] ?: env["HTTP_PROXY"]
        val httpProxy = parseProxy(httpProxyUrl)
        httpProxy?.let { configureHttpProxy(it) }

        // Parse HTTPS proxy (lowercase takes precedence)
        val httpsProxyUrl = env["https_proxy"] ?: env["HTTPS_PROXY"]
        val httpsProxy = parseProxy(httpsProxyUrl)
        httpsProxy?.let { configureHttpsProxy(it) }

        // Parse NO_PROXY (lowercase takes precedence)
        val noProxyValue = env["no_proxy"] ?: env["NO_PROXY"]
        val nonProxyHosts = parseNoProxy(noProxyValue)
        nonProxyHosts?.let {
            System.setProperty("http.nonProxyHosts", it)
            logger.debug("Set http.nonProxyHosts=$it")
        }

        if (httpProxy == null && httpsProxy == null) {
            logger.debug("No proxy configuration found in environment")
        }
    }

    /**
     * Parses a proxy URL string into a ProxyConfig.
     *
     * Supports formats:
     * - http://proxy.example.com:8080
     * - http://user:pass@proxy.example.com:8080
     * - proxy.example.com:8080 (defaults to http://)
     * - proxy.example.com (defaults to port 8080)
     *
     * @param proxyUrl The proxy URL string (can be null)
     * @return ProxyConfig object or null if URL is null/blank/invalid
     */
    private fun parseProxy(proxyUrl: String?): ProxyConfig? {
        if (proxyUrl.isNullOrBlank()) return null

        return try {
            // Add scheme if missing (assume http)
            val urlWithScheme = if (!proxyUrl.contains("://")) {
                "http://$proxyUrl"
            } else {
                proxyUrl
            }

            val uri = URI(urlWithScheme)
            val host = uri.host ?: throw IllegalArgumentException("Missing host in proxy URL")
            val port = if (uri.port > 0) uri.port else 8080

            // Parse authentication if present
            val userInfo = uri.userInfo
            val (user, password) = if (userInfo != null && userInfo.contains(':')) {
                val parts = userInfo.split(':', limit = 2)
                parts[0] to parts[1]
            } else if (userInfo != null) {
                userInfo to null
            } else {
                null to null
            }

            ProxyConfig(host, port, user, password)
        } catch (e: Exception) {
            logger.warn("Failed to parse proxy URL '$proxyUrl': ${e.message}")
            null
        }
    }

    /**
     * Configures HTTP proxy system properties.
     * Registers password with SecretRegistry for log masking.
     */
    private fun configureHttpProxy(proxy: ProxyConfig) {
        System.setProperty("http.proxyHost", proxy.host)
        System.setProperty("http.proxyPort", proxy.port.toString())
        logger.info("HTTP proxy configured: ${proxy.host}:${proxy.port}")

        proxy.user?.let {
            System.setProperty("http.proxyUser", it)
            logger.debug("HTTP proxy user: $it")
        }

        proxy.password?.let {
            // Register password for masking in logs before setting property
            SecretRegistry.register(it)
            System.setProperty("http.proxyPassword", it)
            logger.debug("HTTP proxy password set.")
        }
    }

    /**
     * Configures HTTPS proxy system properties.
     * Registers password with SecretRegistry for log masking.
     */
    private fun configureHttpsProxy(proxy: ProxyConfig) {
        System.setProperty("https.proxyHost", proxy.host)
        System.setProperty("https.proxyPort", proxy.port.toString())
        logger.info("HTTPS proxy configured: ${proxy.host}:${proxy.port}")

        proxy.user?.let {
            System.setProperty("https.proxyUser", it)
            logger.debug("HTTPS proxy user: $it")
        }

        proxy.password?.let {
            // Register password for masking in logs before setting property
            SecretRegistry.register(it)
            System.setProperty("https.proxyPassword", it)
            logger.debug("HTTPS proxy password: <masked>")
        }
    }

    /**
     * Parses NO_PROXY environment variable and converts to Java's http.nonProxyHosts format.
     *
     * Why format conversion is necessary:
     * - NO_PROXY uses comma-separated list: "localhost,127.0.0.1,.example.com,10.0.0.0/8"
     * - Java uses pipe-separated list: "localhost|127.0.0.1|*.example.com|10.0.0.*"
     * - Domain wildcards differ: ".example.com" → "*.example.com"
     * - CIDR notation needs conversion: "10.0.0.0/8" → "10.0.0.*" (simplified)
     *
     * @param noProxy Comma-separated list of hosts/domains/CIDRs
     * @return Pipe-separated list in Java format, or null if input is null/blank
     */
    private fun parseNoProxy(noProxy: String?): String? {
        if (noProxy.isNullOrBlank()) return null

        val hosts = noProxy.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("|") { convertNoProxyEntry(it) }

        return hosts.ifEmpty { null }
    }

    /**
     * Converts a single NO_PROXY entry to Java's nonProxyHosts format.
     *
     * Conversions:
     * - ".example.com" → "*.example.com" (domain wildcard)
     * - "example.com" → "example.com" (exact match)
     * - "10.0.0.0/8" → "10.0.0.*" (simplified CIDR - only handles /8, /16, /24)
     * - "*.example.com" → "*.example.com" (already in Java format)
     */
    private fun convertNoProxyEntry(entry: String): String {
        return when {
            // Domain wildcard: .example.com → *.example.com
            entry.startsWith('.') -> "*${entry}"

            // CIDR notation: 10.0.0.0/8 → 10.* (simplified)
            entry.contains('/') -> {
                val parts = entry.split('/')
                val ip = parts[0]
                val prefix = parts.getOrNull(1)?.toIntOrNull() ?: 0

                val octets = ip.split('.')
                when {
                    prefix <= 8 -> "${octets[0]}.*"
                    prefix <= 16 -> "${octets[0]}.${octets.getOrNull(1) ?: "0"}.*"
                    prefix <= 24 -> "${octets[0]}.${octets.getOrNull(1) ?: "0"}.${octets.getOrNull(2) ?: "0"}.*"
                    else -> entry // Keep original for /32 or invalid
                }
            }

            // Already in correct format or exact hostname
            else -> entry
        }
    }

    /**
     * Clears all proxy-related system properties.
     * Used primarily for testing to reset state between tests.
     */
    fun clearProxyConfiguration() {
        listOf(
            "http.proxyHost", "http.proxyPort", "http.proxyUser", "http.proxyPassword",
            "https.proxyHost", "https.proxyPort", "https.proxyUser", "https.proxyPassword",
            "http.nonProxyHosts"
        ).forEach { System.clearProperty(it) }
    }

    /**
     * Data class representing parsed proxy configuration.
     */
    private data class ProxyConfig(
        val host: String,
        val port: Int,
        val user: String? = null,
        val password: String? = null
    )
}
