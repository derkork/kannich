package dev.kannich.pulumi

import dev.kannich.stdlib.Arch
import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.fail
import dev.kannich.tools.ArchiveToolInstaller
import dev.kannich.tools.Cache
import dev.kannich.tools.Fs
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Provides Pulumi infrastructure management for Kannich pipelines.
 * Downloads and installs the specified Pulumi version on first use.
 *
 * Uses a `.kannich_pulumi` directory in the job's working directory as PULUMI_HOME,
 * so multiple invocations within the same job share credentials and state.
 * Provider plugins are symlinked from there into the Kannich cache so they are
 * reused across runs, while credentials and workspace state remain local to the
 * working directory and are never written into the tool cache.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val pulumi = Pulumi("3.252.0")
 *
 *     val deploy = job("Deploy Infrastructure") {
 *         pulumi.exec("up", "--yes")
 *     }
 * }
 * ```
 */
class Pulumi(version: String) : ArchiveToolInstaller("pulumi", version, archiveStripComponents = 1) {

    override fun getMainExecutable(): String = "pulumi"

    override fun getDownloadUrl(): String {
        val arch = when (val current = Arch.current) {
            is Arch.Amd64 -> "x64"
            is Arch.Arm64 -> "arm64"
            is Arch.Unknown -> fail("Unsupported architecture: ${current.archString}")
        }
        return "https://get.pulumi.com/releases/sdk/pulumi-v${version}-linux-${arch}.tar.gz"
    }

    override suspend fun exec(vararg args: String, silent: Boolean, allowFailure: Boolean): ExecResult {
        Cache.ensureDir(pluginsCacheKey)
        val context = JobContext.current()
        val pulumiHome = "${context.workingDir}/.kannich_pulumi"
        if (!Files.exists(Paths.get(pulumiHome))) {
            // First invocation in this working dir: set up the PULUMI_HOME skeleton.
            Fs.mkdir(pulumiHome)
            Files.createSymbolicLink(Paths.get("$pulumiHome/plugins"), Paths.get(Cache.path(pluginsCacheKey)))
        }
        return context.withEnv(mapOf("PULUMI_HOME" to pulumiHome)) {
            super.exec(*args, silent = silent, allowFailure = allowFailure)
        }
    }

    private val pluginsCacheKey = "tools/pulumi/${Arch.current.archString}/plugins"
}
