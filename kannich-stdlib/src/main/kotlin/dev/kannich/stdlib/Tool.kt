package dev.kannich.stdlib

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
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
 * Executes the given suspend block and logs the time taken.
 *
 * @param label A descriptive label for what is being timed (e.g., "Job build")
 * @param block The suspend code block to execute and time
 * @return The result of the block
 */
suspend fun <T> timedSuspend(label: String, block: suspend () -> T): T {
    val start = System.nanoTime()
    val result = block()
    val duration = System.nanoTime() - start
    val seconds = duration / 1_000_000_000.0
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

/**
 * Global registry for secret values that should be masked in log output.
 * Thread-safe and persists for entire execution lifecycle.
 */
object SecretRegistry {
    private val secrets: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun register(value: String) {
        if (value.isNotBlank()) {
            secrets.add(value)
        }
    }

    fun getSecrets(): Set<String> = secrets.toSet()

    fun clear() {
        secrets.clear()
    }
}

/**
 * Marks a string value as a secret, registering it for masking in log output.
 * Returns the original value unchanged for use in string interpolation.
 *
 * Empty/blank values are ignored (not registered).
 * Null values return an empty string.
 */
fun secret(value: String?): String {
    if (value.isNullOrBlank()) return value ?: ""
    SecretRegistry.register(value)
    return value
}
