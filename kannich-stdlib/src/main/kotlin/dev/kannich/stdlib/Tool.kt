package dev.kannich.stdlib

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
