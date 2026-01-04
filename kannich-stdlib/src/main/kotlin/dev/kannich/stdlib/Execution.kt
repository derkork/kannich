package dev.kannich.stdlib

/**
 * Represents an execution plan that orchestrates jobs.
 */
class Execution internal constructor(
    val name: String,
    val description: String? = null,
    val steps: List<ExecutionStep>
)

sealed interface ExecutionStep

class JobExecutionStep(val job: Job) : ExecutionStep
class ExecutionReference(val execution: Execution) : ExecutionStep
class SequentialSteps(val steps: List<ExecutionStep>) : ExecutionStep
class ParallelSteps(val steps: List<ExecutionStep>) : ExecutionStep

class ExecutionBuilder(private val name: String, private val description: String? = null) : Logging by LoggingImpl("Execution $name") {
    /**
     * The steps of the execution. These are executed in sequential order.
     */
    private val steps = mutableListOf<ExecutionStep>()

    /**
     * Read-only access to environment variables at execution definition time.
     * Includes both system env vars and extra env vars passed via `-e` CLI args.
     */
    fun getEnv(name: String): String? = PipelineEnv.getEnv(name)

    fun job(job: Job) {
        steps.add(JobExecutionStep(job))
    }

    fun job(name: String, description: String? = null, block: suspend JobScope.() -> Unit) {
        val builder = JobBuilder(name, description)
        builder.setBlock(block)
        steps.add(JobExecutionStep(builder.build()))
    }

    fun execution(execution: Execution) {
        steps.add(ExecutionReference(execution))
    }

    fun sequentially(block: SequentialBuilder.() -> Unit) {
        steps.add(SequentialBuilder(name).apply(block).build())
    }

    fun parallel(block: ParallelBuilder.() -> Unit) {
        steps.add(ParallelBuilder(name).apply(block).build())
    }

    internal fun build(): Execution = Execution(name, description, steps.toList())
}

class SequentialBuilder(val name: String) : Logging by LoggingImpl("Execution $name") {
    private val steps = mutableListOf<ExecutionStep>()

    /**
     * Read-only access to environment variables at execution definition time.
     * Includes both system env vars and extra env vars passed via `-e` CLI args.
     */
    fun getEnv(name: String): String? = PipelineEnv.getEnv(name)


    fun job(job: Job) {
        steps.add(JobExecutionStep(job))
    }

    fun job(name: String, description: String? = null, block: suspend JobScope.() -> Unit) {
        val builder = JobBuilder(name, description)
        builder.setBlock(block)
        steps.add(JobExecutionStep(builder.build()))
    }

    fun parallel(block: ParallelBuilder.() -> Unit) {
        steps.add(ParallelBuilder(name).apply(block).build())
    }

    internal fun build(): SequentialSteps = SequentialSteps(steps.toList())
}

class ParallelBuilder(name: String) : Logging by LoggingImpl("Execution $name") {
    private val steps = mutableListOf<ExecutionStep>()

    /**
     * Read-only access to environment variables at execution definition time.
     * Includes both system env vars and extra env vars passed via `-e` CLI args.
     */
    fun getEnv(name: String): String? = PipelineEnv.getEnv(name)

    fun job(job: Job) {
        steps.add(JobExecutionStep(job))
    }

    fun job(name: String, description: String? = null, block: suspend JobScope.() -> Unit) {
        val builder = JobBuilder(name, description)
        builder.setBlock(block)
        steps.add(JobExecutionStep(builder.build()))
    }

    internal fun build(): ParallelSteps = ParallelSteps(steps.toList())
}
