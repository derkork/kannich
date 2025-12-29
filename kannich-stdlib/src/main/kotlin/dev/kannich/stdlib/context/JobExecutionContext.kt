package dev.kannich.stdlib.context

/**
 * Context available during job execution.
 * Provides access to command execution and the pipeline context.
 *
 * This context is set by the execution engine for each job and made available
 * via thread-local so that tools can access it without explicit parameter passing.
 */
class JobExecutionContext(
    val pipelineContext: PipelineContext,
    val executor: CommandExecutor,
    val workingDir: String
) {
    companion object {
        private val current = ThreadLocal<JobExecutionContext>()

        /**
         * Gets the current job execution context.
         * @throws IllegalStateException if no context is available
         */
        fun current(): JobExecutionContext = current.get()
            ?: error("No JobExecutionContext available. Are you inside a job block during execution?")

        /**
         * Gets the current job execution context, or null if not available.
         */
        fun currentOrNull(): JobExecutionContext? = current.get()

        /**
         * Executes a block with the given context set as current.
         * Also sets the pipeline context for the duration of the block.
         */
        fun <T> withContext(ctx: JobExecutionContext, block: () -> T): T {
            val prev = current.get()
            current.set(ctx)
            return try {
                PipelineContext.withContext(ctx.pipelineContext) {
                    block()
                }
            } finally {
                if (prev != null) {
                    current.set(prev)
                } else {
                    current.remove()
                }
            }
        }
    }
}
