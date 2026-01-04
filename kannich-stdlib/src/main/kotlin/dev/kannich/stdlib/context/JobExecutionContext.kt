package dev.kannich.stdlib.context

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Context available during job execution.
 * Provides access to command execution and the pipeline context.
 *
 * Access the current context via [current] suspend function.
 */
class JobExecutionContext(
    val pipelineContext: PipelineContext,
    val executor: CommandExecutor,
    val workingDir: String
) : CoroutineContext.Element {

    companion object Key : CoroutineContext.Key<JobExecutionContext>

    override val key: CoroutineContext.Key<JobExecutionContext> get() = Key
}

/**
 * Gets the current job execution context.
 * @throws IllegalStateException if no context is available
 */
suspend fun currentJobExecutionContext(): JobExecutionContext =
    coroutineContext[JobExecutionContext]
        ?: error("No JobExecutionContext available. Are you inside a job block during execution?")

/**
 * Gets the current job execution context, or null if not available.
 */
suspend fun currentJobExecutionContextOrNull(): JobExecutionContext? =
    coroutineContext[JobExecutionContext]
