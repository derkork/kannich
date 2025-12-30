package dev.kannich.core

import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages graceful shutdown of Kannich resources.
 * Registers a JVM shutdown hook to clean up containers when SIGINT/SIGTERM is received.
 */
object ShutdownManager {
    private val logger = LoggerFactory.getLogger(ShutdownManager::class.java)
    private val resources = CopyOnWriteArrayList<Closeable>()
    private val hookRegistered = AtomicBoolean(false)
    private val shuttingDown = AtomicBoolean(false)

    /**
     * Registers a resource for cleanup on shutdown.
     * The resource's close() method will be called when the JVM shuts down.
     */
    fun register(resource: Closeable) {
        ensureHookRegistered()
        resources.add(resource)
        logger.debug("Registered resource for shutdown: ${resource::class.simpleName}")
    }

    /**
     * Unregisters a resource (call this after normal cleanup to avoid double-close).
     */
    fun unregister(resource: Closeable) {
        resources.remove(resource)
        logger.debug("Unregistered resource: ${resource::class.simpleName}")
    }

    /**
     * Returns true if we're currently in a shutdown sequence.
     */
    fun isShuttingDown(): Boolean = shuttingDown.get()

    private fun ensureHookRegistered() {
        if (hookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(Thread({
                shutdown()
            }, "kannich-shutdown"))
            logger.debug("Shutdown hook registered")
        }
    }

    private fun shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return // Already shutting down
        }

        logger.info("Shutdown signal received, cleaning up...")

        // Close resources in reverse order (LIFO)
        val toClose = resources.reversed()
        for (resource in toClose) {
            try {
                logger.debug("Closing: ${resource::class.simpleName}")
                resource.close()
            } catch (e: Exception) {
                logger.warn("Error closing ${resource::class.simpleName}: ${e.message}")
            }
        }
        resources.clear()

        logger.info("Shutdown complete")
    }
}
