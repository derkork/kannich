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
     *
     * Use `artifacts { }` within the job block to collect build artifacts.
     */
    fun job(name: String, block: JobScope.() -> Unit): Job {
        val builder = JobBuilder(name)
        builder.setBlock(block)
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
