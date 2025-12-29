package dev.kannich.stdlib

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
    private val steps = mutableListOf<ExecutionStep>()

    fun job(job: Job) {
        steps.add(JobExecutionStep(job))
    }

    fun job(name: String, block: JobBuilder.() -> Unit) {
        val job = JobBuilder(name).apply(block).build()
        steps.add(JobExecutionStep(job))
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

    fun job(job: Job) {
        steps.add(JobExecutionStep(job))
    }

    fun parallel(block: ParallelBuilder.() -> Unit) {
        steps.add(ParallelBuilder().apply(block).build())
    }

    internal fun build(): SequentialSteps = SequentialSteps(steps.toList())
}

class ParallelBuilder {
    private val steps = mutableListOf<ExecutionStep>()

    fun job(job: Job) {
        steps.add(JobExecutionStep(job))
    }

    internal fun build(): ParallelSteps = ParallelSteps(steps.toList())
}
