package dev.kannich.trivy

import dev.kannich.stdlib.Arch
import dev.kannich.stdlib.ExecResult
import dev.kannich.stdlib.fail
import dev.kannich.tools.ArchiveToolInstaller
import dev.kannich.tools.Cache
import dev.kannich.tools.Fs

/**
 * Provides Trivy security scanning for Kannich pipelines.
 * Downloads and installs the specified Trivy version on first use.
 *
 * Trivy is a comprehensive security scanner that can scan:
 * - Container images
 * - Filesystems
 * - Git repositories
 * - Kubernetes clusters
 * - And more
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val trivy = Trivy("0.58.0")
 *
 *     val scan = job("Security Scan") {
 *         // Scan a container image
 *         trivy.exec("image", "myapp:latest")
 *
 *         // Scan the filesystem
 *         trivy.exec("fs", ".")
 *
 *         // Scan with custom options
 *         trivy.exec("image", "--severity", "HIGH,CRITICAL", "--exit-code", "1", "myapp:latest")
 *     }
 * }
 * ```
 */
class Trivy(version: String) : ArchiveToolInstaller("trivy", version) {

    @Deprecated("Use getInstallPath() instead", ReplaceWith("getInstallPath()"), DeprecationLevel.WARNING)
    suspend fun home(): String = getInstallPath()

    override fun getMainExecutable(): String = "trivy"

    override suspend fun exec(vararg args: String, silent: Boolean, allowFailure: Boolean): ExecResult {
        val dbCacheKey = "tools/trivy/db"
        Cache.ensureDir(dbCacheKey)
        return super.exec("--cache-dir", Cache.path(dbCacheKey), *args, silent = silent, allowFailure = allowFailure)
    }

    /**
     * Scans a filesystem for vulnerabilities and generates an HTML report.
     *
     * @param reportPath Path to save the HTML report, defaults to 'target/report.html'
     * @param severity Severity level to filter vulnerabilities, defaults to 'CRITICAL,HIGH'
     */
    suspend fun scanFs(reportPath: String = "target/report.html", severity: String = "CRITICAL,HIGH") {
        Fs.mkdir(Fs.getParent(reportPath))
        exec(
            "fs", "--quiet", "--scanners", "vuln", ".",
            "--severity", severity,
            "--ignore-unfixed",
            "--exit-code", "1",
            "--format", "template",
            "--template", "@${getInstallPath()}/contrib/html.tpl",
            "-o", reportPath
        )
    }

    override fun getDownloadUrl(): String {
        val arch = when (val current = Arch.current) {
            is Arch.Amd64 -> "64bit"
            is Arch.Arm64 -> "ARM64"
            is Arch.Unknown -> fail("Unsupported architecture: ${current.archString}")
        }
        return "https://github.com/aquasecurity/trivy/releases/download/v$version/trivy_${version}_Linux-$arch.tar.gz"
    }
}
