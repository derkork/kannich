package dev.kannich.java

import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.fail
import dev.kannich.tools.Cache
import dev.kannich.tools.Compressor
import dev.kannich.tools.Fs
import dev.kannich.tools.Shell
import dev.kannich.tools.Web
import dev.kannich.stdlib.Arch
import dev.kannich.stdlib.Tool
import dev.kannich.tools.ArchiveToolInstaller
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Provides Java SDK management for Kannich pipelines.
 * Downloads and installs the specified Java version on first use.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val java = Java("21")
 *     val compile = job("Compile") {
 *         java.exec("-version")  // prints Java version
 *     }
 * }
 * ```
 */
class Java(version: String) : ArchiveToolInstaller("java", version, archiveStripComponents = 1) {
    @Deprecated("Use getInstallPath() instead", ReplaceWith("getInstallPath()"), DeprecationLevel.WARNING)
    suspend fun home(): String = getInstallPath()

    override fun getMainExecutable(): String = "bin/java"

    override suspend fun exec(vararg args: String, silent: Boolean, allowFailure: Boolean) : ExecResult {
        val homeDir = getInstallPath()
        // ensure JAVA_HOME is set when running the command
        return JobContext.current().withEnv(mapOf("JAVA_HOME" to homeDir)) {
            super.exec(*args, silent = silent, allowFailure = allowFailure)
        }

    }

    override fun getDownloadUrl(): String {
        val arch = when (val current = Arch.current) {
            is Arch.Amd64 -> "x64"
            is Arch.Arm64 -> "aarch64"
            is Arch.Unknown -> fail("Unsupported architecture: ${current.archString}")
        }

        return "https://api.adoptium.net/v3/binary/latest/$version/ga/linux/$arch/jdk/hotspot/normal/eclipse"
    }
}
