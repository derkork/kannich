package dev.kannich.stdlib.context

import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.CoroutineContext

/**
 * Runtime context available during pipeline execution.
 * Provides access to cache directory, project directory, and environment variables.
 *
 * This context is available via both ThreadLocal (for regular code) and
 * CoroutineContext (for coroutine-based code). When running in coroutines,
 * the context is automatically preserved across suspension points.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class PipelineContext(
    val cacheDir: String,
    val projectDir: String,
    val env: Map<String, String> = emptyMap()
) : CopyableThreadContextElement<PipelineContext?> {

    companion object Key : CoroutineContext.Key<PipelineContext> {
        private val threadLocal = ThreadLocal<PipelineContext>()

        /**
         * Gets the current pipeline context.
         * Checks coroutine context first (if in a coroutine), then falls back to ThreadLocal.
         * @throws IllegalStateException if no context is available
         */
        fun current(): PipelineContext = currentOrNull()
            ?: error("No PipelineContext available. Are you inside a job block during execution?")

        /**
         * Gets the current pipeline context, or null if not available.
         * Checks coroutine context first (if in a coroutine), then falls back to ThreadLocal.
         */
        fun currentOrNull(): PipelineContext? = threadLocal.get()

        /**
         * Executes a block with the given context set as current.
         * Works for both regular threads and coroutines.
         */
        fun <T> withContext(ctx: PipelineContext, block: () -> T): T {
            val prev = threadLocal.get()
            threadLocal.set(ctx)
            return try {
                block()
            } finally {
                if (prev != null) {
                    threadLocal.set(prev)
                } else {
                    threadLocal.remove()
                }
            }
        }
    }

    override val key: CoroutineContext.Key<PipelineContext> get() = Key

    /**
     * Called when a coroutine with this element starts or resumes on a thread.
     * Sets the ThreadLocal and returns the previous value.
     */
    override fun updateThreadContext(context: CoroutineContext): PipelineContext? {
        val prev = threadLocal.get()
        threadLocal.set(this)
        return prev
    }

    /**
     * Called when a coroutine with this element suspends or completes.
     * Restores the previous ThreadLocal value.
     */
    override fun restoreThreadContext(context: CoroutineContext, oldState: PipelineContext?) {
        if (oldState != null) {
            threadLocal.set(oldState)
        } else {
            threadLocal.remove()
        }
    }

    /**
     * Creates a copy of this element for child coroutines.
     * Child coroutines inherit the same context.
     */
    override fun copyForChild(): CopyableThreadContextElement<PipelineContext?> = this

    /**
     * Merges this element with a child's overwriting element.
     * The child's element takes precedence.
     */
    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext =
        overwritingElement
}
