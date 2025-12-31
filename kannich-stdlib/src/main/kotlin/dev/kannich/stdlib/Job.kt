package dev.kannich.stdlib

/**
 * Represents a single CI job that runs in isolation.
 * The job's block is executed during job execution, not during pipeline definition.
 *
 * Artifacts can be collected during job execution using the `artifacts { }` DSL
 * within the job block.
 */
class Job internal constructor(
    val name: String,
    val block: JobScope.() -> Unit
)

/**
 * Builder for constructing Job instances.
 */
@KannichDsl
class JobBuilder(private val name: String) {
    private var jobBlock: (JobScope.() -> Unit)? = null

    internal fun setBlock(block: JobScope.() -> Unit) {
        jobBlock = block
    }

    internal fun build(): Job = Job(name, jobBlock ?: {})
}
