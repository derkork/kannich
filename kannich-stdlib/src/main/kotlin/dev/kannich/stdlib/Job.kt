package dev.kannich.stdlib

/**
 * Represents a single CI job that runs in isolation.
 * The job's block is executed during job execution, not during pipeline definition.
 */
class Job internal constructor(
    val name: String,
    val block: JobScope.() -> Unit,
    val artifacts: ArtifactSpec?
)

/**
 * Builder for constructing Job instances.
 */
@KannichDsl
class JobBuilder(private val name: String) {
    private var artifactSpec: ArtifactSpec? = null
    private var jobBlock: (JobScope.() -> Unit)? = null

    internal fun setBlock(block: JobScope.() -> Unit) {
        jobBlock = block
    }

    internal fun artifacts(block: ArtifactSpecBuilder.() -> Unit) {
        artifactSpec = ArtifactSpecBuilder().apply(block).build()
    }

    internal fun build(): Job = Job(name, jobBlock ?: {}, artifactSpec)
}
