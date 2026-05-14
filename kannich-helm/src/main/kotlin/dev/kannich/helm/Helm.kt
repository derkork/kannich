package dev.kannich.helm

import dev.kannich.stdlib.Arch
import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.Tool
import dev.kannich.stdlib.fail
import dev.kannich.tools.ArchiveToolInstaller
import dev.kannich.tools.Cache
import dev.kannich.tools.Compressor
import dev.kannich.tools.Fs
import dev.kannich.tools.Shell
import dev.kannich.tools.Web
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Provides Helm for Kannich pipelines.
 * Downloads and installs the specified Helm version on first use.
 *
 * Helm is the package manager for Kubernetes that helps you:
 * - Install and manage Kubernetes applications
 * - Package and share applications as Helm charts
 * - Manage releases and rollbacks
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val helm = Helm("3.14.0")
 *
 *     val deploy = job("Deploy to Kubernetes") {
 *         // Install a chart
 *         helm.exec("install", "my-release", "my-chart")
 *
 *         // Upgrade a release
 *         helm.exec("upgrade", "--install", "my-release", "my-chart")
 *
 *         // List releases
 *         helm.exec("list")
 *
 *         // Add a repository
 *         helm.exec("repo", "add", "bitnami", "https://charts.bitnami.com/bitnami")
 *     }
 * }
 * ```
 */
class Helm(version: String) : ArchiveToolInstaller("helm", version, archiveStripComponents = 1) {
    override fun getMainExecutable(): String = "helm"

    override fun getDownloadUrl(): String {
        val arch = when (val current = Arch.current) {
            is Arch.Amd64 -> "amd64"
            is Arch.Arm64 -> "arm64"
            is Arch.Unknown -> fail("Unsupported architecture: ${current.archString}")
        }
        return "https://get.helm.sh/helm-v$version-linux-$arch.tar.gz"
    }
}
