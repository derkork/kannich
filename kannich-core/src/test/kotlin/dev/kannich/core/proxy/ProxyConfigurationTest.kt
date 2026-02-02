package dev.kannich.core.proxy

import dev.kannich.stdlib.SecretRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

class ProxyConfigurationTest : FunSpec({

    beforeEach {
        // Clear proxy configuration and secret registry before each test
        ProxyConfiguration.clearProxyConfiguration()
        SecretRegistry.clear()
    }

    afterEach {
        // Clean up after each test
        ProxyConfiguration.clearProxyConfiguration()
        SecretRegistry.clear()
    }

    context("HTTP proxy configuration") {
        test("configure HTTP proxy with basic URL") {
            val env = mapOf("HTTP_PROXY" to "http://proxy.example.com:8080")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.proxyHost") shouldBe "proxy.example.com"
            System.getProperty("http.proxyPort") shouldBe "8080"
            System.getProperty("http.proxyUser").shouldBeNull()
            System.getProperty("http.proxyPassword").shouldBeNull()
        }

        test("configure HTTP proxy with authentication") {
            val env = mapOf("HTTP_PROXY" to "http://testuser:testpass123@proxy.example.com:8080")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.proxyHost") shouldBe "proxy.example.com"
            System.getProperty("http.proxyPort") shouldBe "8080"
            System.getProperty("http.proxyUser") shouldBe "testuser"
            System.getProperty("http.proxyPassword") shouldBe "testpass123"

            // Verify password is registered as a secret
            SecretRegistry.getSecrets().contains("testpass123") shouldBe true
        }

        test("configure HTTP proxy without scheme defaults to http") {
            val env = mapOf("HTTP_PROXY" to "proxy.example.com:3128")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.proxyHost") shouldBe "proxy.example.com"
            System.getProperty("http.proxyPort") shouldBe "3128"
        }

        test("configure HTTP proxy without port defaults to 8080") {
            val env = mapOf("HTTP_PROXY" to "http://proxy.example.com")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.proxyHost") shouldBe "proxy.example.com"
            System.getProperty("http.proxyPort") shouldBe "8080"
        }

        test("lowercase http_proxy variable is respected") {
            val env = mapOf("http_proxy" to "http://proxy.example.com:9090")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.proxyHost") shouldBe "proxy.example.com"
            System.getProperty("http.proxyPort") shouldBe "9090"
        }

        test("lowercase http_proxy takes precedence over uppercase") {
            val env = mapOf(
                "HTTP_PROXY" to "http://uppercase.example.com:8080",
                "http_proxy" to "http://lowercase.example.com:9090"
            )
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.proxyHost") shouldBe "lowercase.example.com"
            System.getProperty("http.proxyPort") shouldBe "9090"
        }
    }

    context("HTTPS proxy configuration") {
        test("configure HTTPS proxy with basic URL") {
            val env = mapOf("HTTPS_PROXY" to "https://secureproxy.example.com:8443")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("https.proxyHost") shouldBe "secureproxy.example.com"
            System.getProperty("https.proxyPort") shouldBe "8443"
            System.getProperty("https.proxyUser").shouldBeNull()
            System.getProperty("https.proxyPassword").shouldBeNull()
        }

        test("configure HTTPS proxy with authentication") {
            val env = mapOf("HTTPS_PROXY" to "https://ssluser:sslpass@secureproxy.example.com:8443")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("https.proxyHost") shouldBe "secureproxy.example.com"
            System.getProperty("https.proxyPort") shouldBe "8443"
            System.getProperty("https.proxyUser") shouldBe "ssluser"
            System.getProperty("https.proxyPassword") shouldBe "sslpass"

            // Verify password is registered as a secret
            SecretRegistry.getSecrets().contains("sslpass") shouldBe true
        }

        test("lowercase https_proxy variable is respected") {
            val env = mapOf("https_proxy" to "https://proxy.example.com:9443")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("https.proxyHost") shouldBe "proxy.example.com"
            System.getProperty("https.proxyPort") shouldBe "9443"
        }

        test("lowercase https_proxy takes precedence over uppercase") {
            val env = mapOf(
                "HTTPS_PROXY" to "https://uppercase.example.com:8443",
                "https_proxy" to "https://lowercase.example.com:9443"
            )
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("https.proxyHost") shouldBe "lowercase.example.com"
            System.getProperty("https.proxyPort") shouldBe "9443"
        }
    }

    context("Separate HTTP and HTTPS proxy configuration") {
        test("configure both HTTP and HTTPS proxies separately") {
            val env = mapOf(
                "HTTP_PROXY" to "http://httpproxy.example.com:8080",
                "HTTPS_PROXY" to "https://httpsproxy.example.com:8443"
            )
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.proxyHost") shouldBe "httpproxy.example.com"
            System.getProperty("http.proxyPort") shouldBe "8080"
            System.getProperty("https.proxyHost") shouldBe "httpsproxy.example.com"
            System.getProperty("https.proxyPort") shouldBe "8443"
        }

        test("configure only HTTP proxy leaves HTTPS unset") {
            val env = mapOf("HTTP_PROXY" to "http://httponly.example.com:8080")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.proxyHost").shouldNotBeNull()
            System.getProperty("https.proxyHost").shouldBeNull()
        }

        test("configure only HTTPS proxy leaves HTTP unset") {
            val env = mapOf("HTTPS_PROXY" to "https://httpsonly.example.com:8443")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("https.proxyHost").shouldNotBeNull()
            System.getProperty("http.proxyHost").shouldBeNull()
        }
    }

    context("NO_PROXY configuration") {
        test("parse simple comma-separated NO_PROXY list") {
            val env = mapOf("NO_PROXY" to "localhost,127.0.0.1")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.nonProxyHosts") shouldBe "localhost|127.0.0.1"
        }

        test("convert domain wildcard from .example.com to *.example.com") {
            val env = mapOf("NO_PROXY" to ".example.com,.internal.corp")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.nonProxyHosts") shouldBe "*.example.com|*.internal.corp"
        }

        test("preserve already wildcarded domains") {
            val env = mapOf("NO_PROXY" to "*.example.com,example.org")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.nonProxyHosts") shouldBe "*.example.com|example.org"
        }

        test("convert CIDR /8 notation") {
            val env = mapOf("NO_PROXY" to "10.0.0.0/8")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.nonProxyHosts") shouldBe "10.*"
        }

        test("convert CIDR /16 notation") {
            val env = mapOf("NO_PROXY" to "192.168.0.0/16")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.nonProxyHosts") shouldBe "192.168.*"
        }

        test("convert CIDR /24 notation") {
            val env = mapOf("NO_PROXY" to "172.16.1.0/24")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.nonProxyHosts") shouldBe "172.16.1.*"
        }

        test("handle CIDR /32 notation as exact match") {
            val env = mapOf("NO_PROXY" to "192.168.1.100/32")
            ProxyConfiguration.configureFromEnvironment(env)

            // /32 is kept as-is since it's a single host
            System.getProperty("http.nonProxyHosts") shouldBe "192.168.1.100/32"
        }

        test("parse complex NO_PROXY with multiple formats") {
            val env = mapOf("NO_PROXY" to "localhost,127.0.0.1,.example.com,10.0.0.0/8,192.168.1.0/24")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.nonProxyHosts") shouldBe "localhost|127.0.0.1|*.example.com|10.*|192.168.1.*"
        }

        test("trim whitespace in NO_PROXY entries") {
            val env = mapOf("NO_PROXY" to " localhost , 127.0.0.1 , .example.com ")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.nonProxyHosts") shouldBe "localhost|127.0.0.1|*.example.com"
        }

        test("ignore empty entries in NO_PROXY") {
            val env = mapOf("NO_PROXY" to "localhost,,127.0.0.1,")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.nonProxyHosts") shouldBe "localhost|127.0.0.1"
        }

        test("lowercase no_proxy variable is respected") {
            val env = mapOf("no_proxy" to "localhost,127.0.0.1")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.nonProxyHosts") shouldBe "localhost|127.0.0.1"
        }

        test("lowercase no_proxy takes precedence over uppercase") {
            val env = mapOf(
                "NO_PROXY" to "uppercase.example.com",
                "no_proxy" to "lowercase.example.com"
            )
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.nonProxyHosts") shouldBe "lowercase.example.com"
        }
    }

    context("Error handling") {
        test("malformed proxy URL logs warning but does not crash") {
            val env = mapOf("HTTP_PROXY" to "http://[invalid")
            ProxyConfiguration.configureFromEnvironment(env)

            // Should not throw exception, proxy just won't be configured
            System.getProperty("http.proxyHost").shouldBeNull()
        }

        test("empty proxy URL is ignored") {
            val env = mapOf("HTTP_PROXY" to "")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.proxyHost").shouldBeNull()
        }

        test("blank proxy URL is ignored") {
            val env = mapOf("HTTP_PROXY" to "   ")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.proxyHost").shouldBeNull()
        }

        test("empty NO_PROXY is ignored") {
            val env = mapOf("NO_PROXY" to "")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.nonProxyHosts").shouldBeNull()
        }

        test("proxy URL with only username and no password") {
            val env = mapOf("HTTP_PROXY" to "http://onlyuser@proxy.example.com:8080")
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.proxyHost") shouldBe "proxy.example.com"
            System.getProperty("http.proxyUser") shouldBe "onlyuser"
            System.getProperty("http.proxyPassword").shouldBeNull()
        }
    }

    context("Secret masking integration") {
        test("HTTP proxy password is registered with SecretRegistry") {
            val env = mapOf("HTTP_PROXY" to "http://user:secretpass123@proxy.example.com:8080")
            ProxyConfiguration.configureFromEnvironment(env)

            SecretRegistry.getSecrets().contains("secretpass123") shouldBe true
        }

        test("HTTPS proxy password is registered with SecretRegistry") {
            val env = mapOf("HTTPS_PROXY" to "https://user:httpspass456@proxy.example.com:8443")
            ProxyConfiguration.configureFromEnvironment(env)

            SecretRegistry.getSecrets().contains("httpspass456") shouldBe true
        }

        test("both HTTP and HTTPS passwords are registered") {
            val env = mapOf(
                "HTTP_PROXY" to "http://user1:pass1@httpproxy.example.com:8080",
                "HTTPS_PROXY" to "https://user2:pass2@httpsproxy.example.com:8443"
            )
            ProxyConfiguration.configureFromEnvironment(env)

            val secrets = SecretRegistry.getSecrets()
            secrets.contains("pass1") shouldBe true
            secrets.contains("pass2") shouldBe true
        }

        test("proxy without password does not register empty secret") {
            val env = mapOf("HTTP_PROXY" to "http://proxy.example.com:8080")
            ProxyConfiguration.configureFromEnvironment(env)

            SecretRegistry.getSecrets().size shouldBe 0
        }
    }

    context("clearProxyConfiguration") {
        test("clears all HTTP proxy properties") {
            System.setProperty("http.proxyHost", "test.example.com")
            System.setProperty("http.proxyPort", "8080")
            System.setProperty("http.proxyUser", "user")
            System.setProperty("http.proxyPassword", "pass")

            ProxyConfiguration.clearProxyConfiguration()

            System.getProperty("http.proxyHost").shouldBeNull()
            System.getProperty("http.proxyPort").shouldBeNull()
            System.getProperty("http.proxyUser").shouldBeNull()
            System.getProperty("http.proxyPassword").shouldBeNull()
        }

        test("clears all HTTPS proxy properties") {
            System.setProperty("https.proxyHost", "test.example.com")
            System.setProperty("https.proxyPort", "8443")
            System.setProperty("https.proxyUser", "user")
            System.setProperty("https.proxyPassword", "pass")

            ProxyConfiguration.clearProxyConfiguration()

            System.getProperty("https.proxyHost").shouldBeNull()
            System.getProperty("https.proxyPort").shouldBeNull()
            System.getProperty("https.proxyUser").shouldBeNull()
            System.getProperty("https.proxyPassword").shouldBeNull()
        }

        test("clears nonProxyHosts property") {
            System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1")

            ProxyConfiguration.clearProxyConfiguration()

            System.getProperty("http.nonProxyHosts").shouldBeNull()
        }
    }

    context("Empty environment") {
        test("no proxy configured with empty environment") {
            val env = emptyMap<String, String>()
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.proxyHost").shouldBeNull()
            System.getProperty("https.proxyHost").shouldBeNull()
            System.getProperty("http.nonProxyHosts").shouldBeNull()
        }

        test("no proxy configured with unrelated environment variables") {
            val env = mapOf(
                "PATH" to "/usr/bin",
                "HOME" to "/home/user",
                "LANG" to "en_US.UTF-8"
            )
            ProxyConfiguration.configureFromEnvironment(env)

            System.getProperty("http.proxyHost").shouldBeNull()
            System.getProperty("https.proxyHost").shouldBeNull()
        }
    }
})
