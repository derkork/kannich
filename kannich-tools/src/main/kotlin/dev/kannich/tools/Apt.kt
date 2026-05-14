package dev.kannich.tools

import dev.kannich.stdlib.fail
import org.slf4j.LoggerFactory

/**
 * Built-in tool for installing APT packages.
 *
 * Usage:
 * ```kotlin
 * job("build") {
 *     Apt.install("gcc", "make", "curl")
 *     Apt.install("gcc=4:11.2.0-1ubuntu1", "cmake=3.22.1-1ubuntu1")  // with versions
 * }
 * ```
 */
object Apt {
    private val logger = LoggerFactory.getLogger(Apt::class.java)

    /**
     * Installs the specified packages using apt-get.
     *
     * @param packages Package specifications (e.g., "gcc=11.2.0", "vim", "build-essential")
     */
    suspend fun install(vararg packages: String) {
        if (packages.isEmpty()) return

        logger.info("Installing packages: ${packages.joinToString(", ")}")

        val updateResult = Shell.execShell("apt-get update")
        if (!updateResult.success) {
            fail("Failed to update APT package lists: ${updateResult.stderr}")
        }

        val pkgList = packages.joinToString(" ") { "'$it'" }
        val result = Shell.execShell("apt-get install -y $pkgList")
        if (!result.success) {
            fail("Failed to install packages ${packages.joinToString(", ")}: ${result.stderr}")
        }
    }
}
