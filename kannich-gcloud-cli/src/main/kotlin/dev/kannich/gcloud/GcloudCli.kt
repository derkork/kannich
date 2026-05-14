package dev.kannich.gcloud

import dev.kannich.stdlib.Arch
import dev.kannich.stdlib.fail
import dev.kannich.tools.ArchiveToolInstaller

/**
 * Provides Google Cloud CLI for Kannich pipelines.
 * Downloads and installs the specified Google Cloud CLI version on first use.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val gcloud = GcloudCli("490.0.0")
 *     job("Check gcloud CLI") {
 *         gcloud.exec("--version")
 *     }
 * }
 * ```
 */
class GcloudCli(version: String) : ArchiveToolInstaller("gcloud-cli", version) {
    override fun getMainExecutable(): String = "google-cloud-sdk/bin/gcloud"


    override fun getDownloadUrl(): String {
        val arch = when (val current = Arch.current) {
            is Arch.Amd64 -> "x86_64"
            is Arch.Arm64 -> "arm"
            is Arch.Unknown -> fail("Unsupported architecture: ${current.archString}")
        }
        return "https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-cli-${version}-linux-${arch}.tar.gz"
    }
}
