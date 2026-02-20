package dev.kannich.stdlib

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

/**
 * Context for job execution. Provides access to common resources and utilities within a job block and for tools.
 * Can be accessed via [current].
 */
class JobContext(
    val env: Map<String, String> = mapOf(),
    val workingDir: String,
    private val cleanupActions: MutableList<suspend () -> Unit> = mutableListOf()
) : CoroutineContext.Element {

    private val logger = LoggerFactory.getLogger(JobContext::class.java)

    companion object {
        suspend fun exists(): Boolean = currentCoroutineContext()[Key] != null
        suspend fun current(): JobContext = currentCoroutineContext()[Key]
            ?: error("No JobContext available. Are you inside a job block during execution?")

        object Key : CoroutineContext.Key<JobContext>
    }

    override val key: CoroutineContext.Key<JobContext> get() = Key

    /**
     * Registers a cleanup action to run when the job completes.
     * Cleanup actions run in reverse order (last registered runs first).
     * Cleanup runs regardless of job success or failure.
     *
     * @param action The cleanup action to execute
     */
    fun onCleanup(action: suspend () -> Unit) {
        cleanupActions.add(action)
    }

    /**
     * Changes the working directory for the duration of the block.
     * The path is relative to the current working directory.
     * After the block completes, the previous working directory is restored.
     *
     * @param path The relative path to change to
     * @param block The block to execute in the new directory
     * @return The result of the block
     * @throws JobFailedException if the directory does not exist
     */
    suspend fun <T> cd(path: String, block: suspend () -> T): T {
        val newWorkingDir = "$workingDir/$path"

        if (!Path.of(newWorkingDir).toFile().exists()) {
            fail("Directory '$path' does not exist (full path: $newWorkingDir)")
        }

        val newCtx = JobContext(
            env = env,
            workingDir = newWorkingDir,
            cleanupActions = cleanupActions
        )
        return withContext(newCtx) {
            block()
        }
    }

    /**
     * Changes the given environment variables for the duration of the block.
     *
     * @param vars The environment variables to set. If a variable is null, it is removed from the environment.
     * @param block The block to execute with the new environment.
     * @return The result of the block.
     */
    suspend fun <T> withEnv(vars: Map<String, String?>, block: suspend () -> T): T {
        val newEnv = env.toMutableMap()
        vars.forEach { (key, value) ->
            if (value == null) {
                newEnv.remove(key)
            } else {
                newEnv[key] = value
            }
        }

        val newCtx = JobContext(
            env = newEnv,
            workingDir = workingDir,
            cleanupActions = cleanupActions
        )

        return withContext(newCtx) {
            block()
        }
    }

    /**
     * Changes the environment PATH variable to include the given tools' paths for the duration of the block.
     *
     * @param tools The tools to include in the PATH variable
     * @param block The block to execute with the new PATH variable
     * @return The result of the block
     */
    suspend fun <T> withTools(vararg tools: Tool, block: suspend () -> T): T {
        val path = env["PATH"]
        val toolsPath = tools.flatMap { it.getToolPaths() }.joinToString(":")
        val finalPath = when (path) {
            null -> toolsPath
            else -> "$toolsPath:$path"
        }

        return withEnv(mapOf("PATH" to finalPath), block)
    }

    /**
     * Runs all registered cleanup actions in reverse order.
     * Exceptions during cleanup are logged but don't prevent other cleanups from running.
     * Used by ExecutionEngine after job execution.
     */
    suspend fun close() {
        cleanupActions.asReversed().forEach { action ->
            runCatching { action() }.onFailure { e ->
                logger.warn("Cleanup action failed: ${e.message}")
            }
        }
    }
}

