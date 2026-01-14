package dev.kannich.stdlib

import kotlinx.coroutines.runBlocking

/**
 * Entry point for defining Kannich pipelines.
 */
fun pipeline(block: suspend PipelineBuilder.() -> Unit): Pipeline {
    val pipelineBuilder = PipelineBuilder()
    runBlocking {
        block(pipelineBuilder)
    }
    return pipelineBuilder.build()
}

class Pipeline internal constructor(
    val executions: Map<String, Execution>
)

@KannichDsl
class PipelineBuilder : Logging by LoggingImpl("Pipeline"), Env by EnvImpl() {
    private val executions = mutableMapOf<String, Execution>()

    /**
     * Defines a job with the given name and execution block.
     * The block is executed when the job runs, not during pipeline definition.
     *
     * Use `artifacts { }` within the job block to collect build artifacts.
     */
    suspend fun job(
        name: String? = null,
        description: String? = null,
        block: suspend JobScope.() -> Unit
    ): Job {
        val builder = JobBuilder(name, description)
        builder.setBlock(block)
        val job = builder.build()
        return job
    }

    suspend fun execution(
        name: String,
        description: String? = null,
        block: suspend ExecutionBuilder.() -> Unit
    ): Execution {
        val executionBuilder = ExecutionBuilder(name, description)
        block(executionBuilder)
        val execution = executionBuilder.build()
        executions[name] = execution
        return execution
    }

    internal fun build(): Pipeline = Pipeline(executions.toMap())
}
