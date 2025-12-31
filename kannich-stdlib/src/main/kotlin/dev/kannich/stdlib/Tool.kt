package dev.kannich.stdlib

import org.slf4j.LoggerFactory
import kotlin.time.measureTimedValue

@PublishedApi
internal val timingLogger = LoggerFactory.getLogger("dev.kannich.timing")

/**
 * Executes the given block and logs the time taken.
 *
 * @param label A descriptive label for what is being timed (e.g., "Job build")
 * @param block The code block to execute and time
 * @return The result of the block
 */
inline fun <T> timed(label: String, block: () -> T): T {
    val (result, duration) = measureTimedValue { block() }
    val seconds = duration.inWholeMilliseconds / 1000.0
    timingLogger.info("$label took ${String.format("%.3f", seconds)}s")
    return result
}

/**
 * Exception thrown when a job execution fails.
 */
class JobFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Throws a JobFailedException with the given message.
 *
 * @param message The error message
 * @throws JobFailedException always
 */
fun fail(message: String): Nothing {
    throw JobFailedException(message)
}
