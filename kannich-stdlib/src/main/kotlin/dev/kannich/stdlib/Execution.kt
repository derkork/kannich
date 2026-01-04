package dev.kannich.stdlib

import dev.kannich.stdlib.Env

/**
 * Represents an execution plan that orchestrates jobs.
 */
class Execution internal constructor(
    val name: String,
    val steps: List<ExecutionStep>
)

sealed interface ExecutionStep

class JobExecutionStep(val job: Job) : ExecutionStep
class ExecutionReference(val execution: Execution) : ExecutionStep
class SequentialSteps(val steps: List<ExecutionStep>) : ExecutionStep
class ParallelSteps(val steps: List<ExecutionStep>) : ExecutionStep

class ExecutionBuilder(private val name: String) {
    /**
     * The steps of the execution. These are executed in sequential order.
     */
    private val steps = mutableListOf<ExecutionStep>()

    /**
     * Read-only access to environment variables at execution definition time.
     */
    val env: Env = Env(System.getenv())

    fun job(job: Job) {
        steps.add(JobExecutionStep(job))
    }

    fun job(name: String, block: JobScope.() -> Unit) {
        val builder = JobBuilder(name)
        builder.setBlock(block)
        steps.add(JobExecutionStep(builder.build()))
    }

    fun execution(execution: Execution) {
        steps.add(ExecutionReference(execution))
    }

    fun sequentially(block: SequentialBuilder.() -> Unit) {
        steps.add(SequentialBuilder().apply(block).build())
    }

    fun parallel(block: ParallelBuilder.() -> Unit) {
        steps.add(ParallelBuilder().apply(block).build())
    }

    internal fun build(): Execution = Execution(name, steps.toList())
}

class SequentialBuilder {
    private val steps = mutableListOf<ExecutionStep>()

    /**
     * Read-only access to environment variables at execution definition time.
     */
    val env: Env = Env(System.getenv())

    fun job(job: Job) {
        steps.add(JobExecutionStep(job))
    }

    fun job(name: String, block: JobScope.() -> Unit) {
        val builder = JobBuilder(name)
        builder.setBlock(block)
        steps.add(JobExecutionStep(builder.build()))
    }

    fun parallel(block: ParallelBuilder.() -> Unit) {
        steps.add(ParallelBuilder().apply(block).build())
    }

    internal fun build(): SequentialSteps = SequentialSteps(steps.toList())
}

class ParallelBuilder {
    private val steps = mutableListOf<ExecutionStep>()

    /**
     * Read-only access to environment variables at execution definition time.
     */
    val env: Env = Env(System.getenv())

    fun job(job: Job) {
        steps.add(JobExecutionStep(job))
    }

    fun job(name: String, block: JobScope.() -> Unit) {
        val builder = JobBuilder(name)
        builder.setBlock(block)
        steps.add(JobExecutionStep(builder.build()))
    }

    internal fun build(): ParallelSteps = ParallelSteps(steps.toList())
}
