package dev.kannich.stdlib

import java.util.Locale

/**
 * Provides information about the current architecture.
 */
sealed class Arch(val archString: String) {


    companion object {
        val current: Arch
            get() = when (System.getProperty("os.arch").lowercase(Locale.getDefault())) {
                "aarch64" -> Arm64
                "amd64", "x86_64" -> Amd64
                else -> Unknown
            }
    }

    object Arm64 : Arch("aarch64")
    object Amd64 : Arch("amd64")
    object Unknown : Arch(System.getProperty("os.arch").lowercase(Locale.getDefault()))
}