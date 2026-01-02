package dev.kannich.stdlib.context

import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.CoroutineContext

/**
 * Context available during job execution.
 * Provides access to command execution and the pipeline context.
 *
 * This context is available via both ThreadLocal (for regular code) and
 * CoroutineContext (for coroutine-based code). When running in coroutines,
 * the context is automatically preserved across suspension points.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class JobExecutionContext(
    val pipelineContext: PipelineContext,
    val executor: CommandExecutor,
    val workingDir: String
) : CopyableThreadContextElement<JobExecutionContext?> {

    companion object Key : CoroutineContext.Key<JobExecutionContext> {
        private val threadLocal = ThreadLocal<JobExecutionContext>()

        /**
         * Gets the current job execution context.
         * @throws IllegalStateException if no context is available
         */
        fun current(): JobExecutionContext = currentOrNull()
            ?: error("No JobExecutionContext available. Are you inside a job block during execution?")

        /**
         * Gets the current job execution context, or null if not available.
         */
        fun currentOrNull(): JobExecutionContext? = threadLocal.get()

        /**
         * Executes a block with the given context set as current.
         * Also sets the pipeline context for the duration of the block.
         * Works for both regular threads and coroutines.
         */
        fun <T> withContext(ctx: JobExecutionContext, block: () -> T): T {
            val prev = threadLocal.get()
            threadLocal.set(ctx)
            return try {
                PipelineContext.withContext(ctx.pipelineContext) {
                    block()
                }
            } finally {
                if (prev != null) {
                    threadLocal.set(prev)
                } else {
                    threadLocal.remove()
                }
            }
        }
    }

    override val key: CoroutineContext.Key<JobExecutionContext> get() = Key

    /**
     * Called when a coroutine with this element starts or resumes on a thread.
     * Sets both JobExecutionContext and PipelineContext ThreadLocals.
     */
    override fun updateThreadContext(context: CoroutineContext): JobExecutionContext? {
        val prev = threadLocal.get()
        threadLocal.set(this)
        // Also update PipelineContext ThreadLocal
        pipelineContext.updateThreadContext(context)
        return prev
    }

    /**
     * Called when a coroutine with this element suspends or completes.
     * Restores both JobExecutionContext and PipelineContext ThreadLocals.
     */
    override fun restoreThreadContext(context: CoroutineContext, oldState: JobExecutionContext?) {
        // Restore PipelineContext first
        pipelineContext.restoreThreadContext(context, oldState?.pipelineContext)
        // Then restore JobExecutionContext
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
    override fun copyForChild(): CopyableThreadContextElement<JobExecutionContext?> = this

    /**
     * Merges this element with a child's overwriting element.
     * The child's element takes precedence.
     */
    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext =
        overwritingElement
}
