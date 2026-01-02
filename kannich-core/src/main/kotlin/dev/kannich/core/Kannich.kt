package dev.kannich.core

import java.util.Properties

class Kannich {
    companion object {
        val VERSION: String = loadVersion()

        private fun loadVersion(): String {
            val props = Properties()
            Kannich::class.java.getResourceAsStream("/version.properties")?.use { props.load(it) }
            val version = props.getProperty("version")?.takeIf { it.isNotBlank() && it != "\${project.version}" }
            return version ?: "development build"
        }
    }
}
