package dev.kannich.zig

import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.Tool
import dev.kannich.stdlib.fail
import dev.kannich.tools.Cache
import dev.kannich.tools.Compressor
import dev.kannich.tools.Shell
import dev.kannich.tools.Web
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Provides the Zig toolchain for Kannich pipelines.
 * Downloads and caches the specified Zig version on first use.
 * Zig includes a C and C++ compiler (`zig cc` / `zig c++`) usable as a drop-in
 * replacement for gcc/g++, making it suitable as a portable C/C++ toolchain
 * for Rust builds, Godot GDExtensions, and other C/C++ projects.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val zig = Zig("0.13.0")
 *
 *     val build = job("Build") {
 *         zig.withToolchain {
 *             Shell.exec("scons", "platform=linux")
 *         }
 *     }
 * }
 * ```
 */
class Zig(val version: String) : Tool {
    private val logger: Logger = LoggerFactory.getLogger(Zig::class.java)

    companion object {
        private const val CACHE_KEY = "zig"
    }

    private suspend fun zigHome(): String = Cache.path("$CACHE_KEY/zig-linux-x86_64-$version")

    override suspend fun getToolPaths() = listOf(zigHome())

    override suspend fun ensureInstalled() {
        val cacheKey = "$CACHE_KEY/zig-linux-x86_64-$version"

        if (Cache.exists(cacheKey)) {
            logger.debug("Zig $version is already installed.")
            return
        }

        logger.info("Zig $version is not installed, downloading.")

        Cache.ensureDir(CACHE_KEY)

        val downloadUrl = "https://ziglang.org/download/$version/zig-linux-x86_64-$version.tar.xz"
        val archive = Web.download(downloadUrl, "zig.tar.xz")
        Compressor.extract(archive, Cache.path(CACHE_KEY))

        if (!Cache.exists(cacheKey)) {
            fail("Zig extraction failed: expected directory ${Cache.path(cacheKey)} not found")
        }

        logger.info("Successfully installed Zig $version.")
    }

    /**
     * Executes `zig` with the given arguments.
     *
     * @param args Arguments to pass to the zig command
     */
    override suspend fun exec(vararg args: String, silent: Boolean, allowFailure: Boolean): ExecResult {
        ensureInstalled()
        val result = Shell.exec("${zigHome()}/zig", *args, silent = silent)

        if (!allowFailure && !result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("zig command failed: $errorMessage")
        }

        return result
    }

    /**
     * Sets CC, CXX, and AR to use the Zig toolchain for the duration of the block.
     * This makes `zig cc` and `zig c++` available as the C/C++ compiler to any
     * build tool invoked inside the block (cargo, scons, cmake, etc.).
     *
     * @param block The block to execute with the Zig toolchain active
     */
    suspend fun <T> withToolchain(block: suspend () -> T): T {
        ensureInstalled()
        val zig = "${zigHome()}/zig"
        return JobContext.current().withEnv(
            mapOf(
                "CC" to "$zig cc",
                "CXX" to "$zig c++",
                "AR" to "$zig ar",
            )
        ) { block() }
    }
}
