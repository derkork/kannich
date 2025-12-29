package dev.kannich.stdlib

/**
 * Entry point for defining Kannich pipelines.
 */
fun pipeline(block: PipelineBuilder.() -> Unit): Pipeline {
    return PipelineBuilder().apply(block).build()
}

class Pipeline internal constructor(
    val jobs: Map<String, Job>,
    val executions: Map<String, Execution>
)

@KannichDsl
class PipelineBuilder {
    private val jobs = mutableMapOf<String, Job>()
    private val executions = mutableMapOf<String, Execution>()

    /**
     * Defines a job with the given name and execution block.
     * The block is executed when the job runs, not during pipeline definition.
     */
    fun job(name: String, block: JobScope.() -> Unit): Job {
        val builder = JobBuilder(name)
        builder.setBlock(block)
        val job = builder.build()
        jobs[name] = job
        return job
    }

    /**
     * Defines a job with artifacts specification.
     * Use this when you need to collect artifacts after job execution.
     */
    fun job(name: String, artifacts: ArtifactSpecBuilder.() -> Unit, block: JobScope.() -> Unit): Job {
        val builder = JobBuilder(name)
        builder.setBlock(block)
        builder.artifacts(artifacts)
        val job = builder.build()
        jobs[name] = job
        return job
    }

    fun execution(name: String, block: ExecutionBuilder.() -> Unit): Execution {
        val execution = ExecutionBuilder(name).apply(block).build()
        executions[name] = execution
        return execution
    }

    internal fun build(): Pipeline = Pipeline(jobs.toMap(), executions.toMap())
}
