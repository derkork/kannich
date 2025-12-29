package dev.kannich.stdlib.context

/**
 * Runtime context available during pipeline execution.
 * Provides access to cache directory, project directory, and environment variables.
 *
 * This context is set by the execution engine and made available via thread-local
 * so that tools can access it without explicit parameter passing.
 */
class PipelineContext(
    val cacheDir: String,
    val projectDir: String,
    val env: Map<String, String> = emptyMap()
) {
    companion object {
        private val current = ThreadLocal<PipelineContext>()

        /**
         * Gets the current pipeline context.
         * @throws IllegalStateException if no context is available
         */
        fun current(): PipelineContext = current.get()
            ?: error("No PipelineContext available. Are you inside a job block during execution?")

        /**
         * Gets the current pipeline context, or null if not available.
         */
        fun currentOrNull(): PipelineContext? = current.get()

        /**
         * Executes a block with the given context set as current.
         */
        fun <T> withContext(ctx: PipelineContext, block: () -> T): T {
            val prev = current.get()
            current.set(ctx)
            return try {
                block()
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
