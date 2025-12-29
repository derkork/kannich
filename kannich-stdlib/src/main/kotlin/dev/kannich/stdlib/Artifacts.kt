package dev.kannich.stdlib

/**
 * Specifies which files to collect as build artifacts.
 */
class ArtifactSpec internal constructor(
    val includes: List<String>,
    val excludes: List<String>
)

class ArtifactSpecBuilder {
    private val includes = mutableListOf<String>()
    private val excludes = mutableListOf<String>()

    fun include(pattern: String) {
        includes.add(pattern)
    }

    fun exclude(pattern: String) {
        excludes.add(pattern)
    }

    internal fun build(): ArtifactSpec = ArtifactSpec(includes.toList(), excludes.toList())
}
