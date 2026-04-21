package dev.kannich.rust

import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.Tool
import dev.kannich.stdlib.fail
import dev.kannich.tools.Cache
import dev.kannich.tools.Fs
import dev.kannich.tools.Shell
import dev.kannich.zig.Zig
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Provides Rust toolchain management for Kannich pipelines via rustup.
 * Installs rustup and the specified toolchain on first use, or the stable
 * toolchain if no version is given.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val cargo = Cargo(Zig("0.13.0"))           // stable Rust, specific Zig
 *     val cargo = Cargo(Zig("0.13.0"), "1.85.0") // specific Rust and Zig versions
 *     val compile = job("Build") {
 *         cargo.exec("build", "--release")
 *     }
 * }
 * ```
 */
class Cargo(private val zig: Zig, val version: String? = null) : Tool {
    private val logger: Logger = LoggerFactory.getLogger(Cargo::class.java)

    companion object {
        private const val CACHE_KEY = "rust"
    }

    private suspend fun cargoHome(): String = Cache.path("$CACHE_KEY/cargo")
    private suspend fun rustupHome(): String = Cache.path("$CACHE_KEY/rustup")

    override suspend fun getToolPaths() = listOf("${cargoHome()}/bin")

    override suspend fun ensureInstalled() {
        val rustupBin = "${rustupHome()}/bin/rustup"

        if (Cache.exists("$CACHE_KEY/rustup/bin/rustup")) {
            if (version != null) {
                logger.info("Rust toolchain $version requested, ensuring it is installed.")
                withRustEnv {
                    Shell.exec(rustupBin, "toolchain", "install", version)
                    Shell.exec(rustupBin, "default", version)
                }
            } else {
                logger.debug("Rust toolchain is already installed.")
            }
            return
        }

        logger.info("Rust toolchain is not installed, running rustup installer.")

        Cache.ensureDir("$CACHE_KEY/cargo")
        Cache.ensureDir("$CACHE_KEY/rustup")

        val installArgs = buildString {
            append("curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --no-modify-path")
            if (version != null) {
                append(" --default-toolchain ")
                append(version)
            }
        }

        val result = withRustEnv {
            Shell.execShell(installArgs)
        }

        if (!result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("Rust installation failed: $errorMessage")
        }

        logger.info("Successfully installed Rust toolchain.")
        pruneCache()
    }

    /**
     * Executes `cargo` with the given arguments.
     *
     * @param args Arguments to pass to the cargo command
     */
    override suspend fun exec(vararg args: String, silent: Boolean, allowFailure: Boolean): ExecResult {
        ensureInstalled()
        val result = withRustEnv {
            Shell.exec("${cargoHome()}/bin/cargo", *args, silent = silent)
        }

        if (!allowFailure && !result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("cargo command failed: $errorMessage")
        }

        pruneCache()
        return result
    }

    // Removes directories that are redundant or transient and should not be persisted in the cache:
    // - cargo/registry/src: extracted crate sources, always regenerated from the compressed archives in registry/cache/
    // - cargo/git/checkouts: checked-out git dependencies, always regenerated from the bare clones in git/db/
    // - rustup/tmp, rustup/downloads: temporary staging directories used only during toolchain installation
    private suspend fun pruneCache() {
        listOf(
            "$CACHE_KEY/cargo/registry/src",
            "$CACHE_KEY/cargo/git/checkouts",
            "$CACHE_KEY/rustup/tmp",
            "$CACHE_KEY/rustup/downloads",
        ).forEach { Fs.delete(Cache.path(it)) }
    }

    private suspend fun <T> withRustEnv(block: suspend () -> T): T {
        val env = mapOf(
            "CARGO_HOME" to cargoHome(),
            "RUSTUP_HOME" to rustupHome(),
        )
        return zig.withToolchain { JobContext.current().withEnv(env) { block() } }
    }
}
