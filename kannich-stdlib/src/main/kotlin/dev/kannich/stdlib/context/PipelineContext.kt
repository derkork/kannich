package dev.kannich.stdlib.context

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Runtime context available during pipeline execution.
 * Provides access to cache directory, project directory, and environment variables.
 *
 * Access the current context via [current] suspend function.
 */
class PipelineContext(
    val cacheDir: String,
    val projectDir: String,
    val env: Map<String, String> = emptyMap()
) : CoroutineContext.Element {

    companion object Key : CoroutineContext.Key<PipelineContext>

    override val key: CoroutineContext.Key<PipelineContext> get() = Key
}

/**
 * Gets the current pipeline context.
 * @throws IllegalStateException if no context is available
 */
suspend fun currentPipelineContext(): PipelineContext =
    coroutineContext[PipelineContext]
        ?: error("No PipelineContext available. Are you inside a job block during execution?")

/**
 * Gets the current pipeline context, or null if not available.
 */
suspend fun currentPipelineContextOrNull(): PipelineContext? =
    coroutineContext[PipelineContext]
