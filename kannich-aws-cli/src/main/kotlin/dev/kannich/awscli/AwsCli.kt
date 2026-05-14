package dev.kannich.awscli

import dev.kannich.stdlib.Arch
import dev.kannich.stdlib.fail
import dev.kannich.tools.ArchiveToolInstaller

/**
 * Provides AWS CLI v2 for Kannich pipelines.
 * Downloads and installs the specified AWS CLI version on first use.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val aws = AwsCli("2.17.44")
 *     job("Check AWS CLI") {
 *         aws.exec("--version")
 *     }
 * }
 * ```
 */
class AwsCli(version: String) : ArchiveToolInstaller("aws-cli", version, archiveFormat = ".zip") {
    override fun getMainExecutable(): String  = "aws/dist/aws"

    override fun getDownloadUrl(): String {
        val arch = when (val current = Arch.current) {
            is Arch.Amd64 -> "x86_64"
            is Arch.Arm64 -> "aarch64"
            is Arch.Unknown -> fail("Unsupported architecture: ${current.archString}")
        }
        return "https://awscli.amazonaws.com/awscli-exe-linux-${arch}-${version}.zip"
    }
}
