package dev.kannich.age

import dev.kannich.stdlib.Arch
import dev.kannich.stdlib.ExecResult
import dev.kannich.tools.ArchiveToolInstaller
import dev.kannich.stdlib.fail

/**
 * Provides age for Kannich pipelines.
 * Downloads and installs the specified age version on first use.
 *
 * age is a simple, modern, and secure file encryption tool:
 * - Encrypt and decrypt files with X25519 keys or passphrases
 * - Generate age key pairs with age-keygen
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val age = Age("1.3.2")
 *
 *     val encrypt = job("Encrypt secrets") {
 *         age.keygen.exec("-o", "key.txt")
 *         age.exec("-r", "age1...", "-o", "secret.age", "secret.txt")
 *     }
 * }
 * ```
 */
class Age(version: String) : ArchiveToolInstaller("age", version, archiveStripComponents = 1) {

    /**
     * age-keygen generates new age key pairs.
     */
    val keygen = SubTool(this, "age-keygen")

    override fun getMainExecutable(): String = "age"

    override fun getDownloadUrl(): String {
        val arch = when (val current = Arch.current) {
            is Arch.Amd64 -> "amd64"
            is Arch.Arm64 -> "arm64"
            is Arch.Unknown -> fail("Unsupported architecture: ${current.archString}")
        }
        return "https://github.com/FiloSottile/age/releases/download/v$version/age-v$version-linux-$arch.tar.gz"
    }

    class SubTool(private val owner: Age, private val name: String) {
        suspend fun exec(vararg args: String, silent: Boolean = false, allowFailure: Boolean = false): ExecResult =
            owner.execWithExecutable(name, *args, silent = silent, allowFailure = allowFailure)
    }
}
