package dev.kannich.godot

import dev.kannich.stdlib.Arch
import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.fail
import dev.kannich.tools.ArchiveToolInstaller
import dev.kannich.tools.Cache

/**
 * Provides GGG (Godot Goodie Grabber) management for Kannich pipelines.
 * Downloads and installs the specified GGG version from GitHub releases on first use.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val ggg = Ggg("0.3.1")
 *     execution("Sync") {
 *          job {
 *              ggg.exec("sync")
 *          }
 *     }
 * }
 * ```
 */
class Ggg(version: String) :
    ArchiveToolInstaller("ggg", version, archiveStripComponents = 1, archiveFormat = ".tar.xz") {

    override fun getMainExecutable(): String = "ggg"

    override suspend fun exec(
        vararg args: String,
        silent: Boolean,
        allowFailure: Boolean
    ): ExecResult {
        val gggCacheDir = "tools/ggg/cache/${Arch.current.archString}"
        Cache.ensureDir(gggCacheDir)
        return JobContext.current()
            .withEnv(mapOf("GGG_CACHE_DIR" to Cache.path(gggCacheDir))) {
                super.exec(*args, silent = silent, allowFailure = allowFailure)
            }
    }

    override fun getDownloadUrl(): String {
        val triple = when (val current = Arch.current) {
            is Arch.Amd64 -> "x86_64-unknown-linux-gnu"
            is Arch.Arm64 -> fail("GGG does not provide Linux ARM64 releases")
            is Arch.Unknown -> fail("Unsupported architecture: ${current.archString}")
        }
        return "https://github.com/godotneers/ggg/releases/download/v$version/ggg-$triple.tar.xz"
    }
}
