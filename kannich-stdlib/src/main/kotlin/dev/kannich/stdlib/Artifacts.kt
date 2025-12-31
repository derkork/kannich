package dev.kannich.stdlib

/**
 * Specifies which files to collect as build artifacts.
 */
class ArtifactSpec internal constructor(
    val includes: List<String>,
    val excludes: List<String>
)

/**
 * Builder for artifact specifications with ant-style glob patterns.
 *
 * Pattern syntax:
 * - `*` matches 0 or more characters except the directory separator (`/`)
 * - `**` matches 0 or more characters including the directory separator (`/`)
 * - `?` matches a single character
 */
@KannichDsl
class ArtifactSpecBuilder {
    private val includes = mutableListOf<String>()
    private val excludes = mutableListOf<String>()

    /**
     * Adds one or more include patterns.
     */
    fun includes(vararg patterns: String) {
        includes.addAll(patterns)
    }

    /**
     * Adds one or more exclude patterns.
     */
    fun excludes(vararg patterns: String) {
        excludes.addAll(patterns)
    }

    internal fun build(): ArtifactSpec = ArtifactSpec(includes.toList(), excludes.toList())
}
