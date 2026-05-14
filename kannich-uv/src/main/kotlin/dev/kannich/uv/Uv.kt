package dev.kannich.uv

import dev.kannich.stdlib.Arch
import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.fail
import dev.kannich.tools.ArchiveToolInstaller
import dev.kannich.tools.Cache

/**
 * Provides UV for Kannich pipelines.
 * Downloads and installs the specified UV version on first use.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val uv = Uv("0.10.7")
 *     job("Check UV") {
 *         uv.exec("--version")
 *     }
 * }
 * ```
 */
class Uv(version: String) : ArchiveToolInstaller("uv", version, archiveStripComponents = 1) {

    @Deprecated("Use getInstallPath() instead", ReplaceWith("getInstallPath()"), DeprecationLevel.WARNING)
    suspend fun home(): String = getInstallPath()

    override fun getMainExecutable(): String = "uv"

    override suspend fun exec(vararg args: String, silent: Boolean, allowFailure: Boolean): ExecResult {
        val uvCacheKey = "tools/uv/cache"
        Cache.ensureDir(uvCacheKey)
        val uvPythonKey = "tools/uv/pythons"
        Cache.ensureDir(uvPythonKey)
        return JobContext.current().withEnv(mapOf(
            "UV_CACHE_DIR" to Cache.path(uvCacheKey),
            "UV_PYTHON_INSTALL_DIR" to Cache.path(uvPythonKey),
            "UV_LINK_MODE" to "copy",  // use copy because we work on an overlayfs and hardlinks won't work there.
        )) {
            super.exec(*args, silent = silent, allowFailure = allowFailure)
        }
    }

    override fun getDownloadUrl(): String {
        val triple = when (val current = Arch.current) {
            is Arch.Amd64 -> "x86_64-unknown-linux-musl"
            is Arch.Arm64 -> "aarch64-unknown-linux-musl"
            is Arch.Unknown -> fail("Unsupported architecture: ${current.archString}")
        }

        return "https://github.com/astral-sh/uv/releases/download/$version/uv-$triple.tar.gz"
    }

}
