package dev.kannich.terraform

import dev.kannich.stdlib.Arch
import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.fail
import dev.kannich.tools.ArchiveToolInstaller

/**
 * Provides Terraform infrastructure management for Kannich pipelines.
 * Downloads and installs the specified Terraform version on first use.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val terraform = Terraform("1.11.0")
 *
 *     val deploy = job("Deploy Infrastructure") {
 *         terraform.exec("init")
 *     }
 * }
 * ```
 */
class Terraform(version: String) : ArchiveToolInstaller("terraform", version, archiveFormat = ".zip") {

    @Deprecated("Use getInstallPath() instead", ReplaceWith("getInstallPath()"), DeprecationLevel.WARNING)
    suspend fun home(): String = getInstallPath()

    override fun getMainExecutable(): String = "terraform"

    override fun getDownloadUrl(): String {
        val arch = when (val current = Arch.current) {
            is Arch.Amd64 -> "amd64"
            is Arch.Arm64 -> "arm64"
            is Arch.Unknown -> fail("Unsupported architecture: ${current.archString}")
        }
        return "https://releases.hashicorp.com/terraform/$version/terraform_${version}_linux_$arch.zip"
    }
}
