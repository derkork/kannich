package dev.kannich.stdlib.context

import dev.kannich.stdlib.fail
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

/**
 * Context for job execution. Provides access to common resources and utilities within a job block and for tools.
 * Can be accessed via [currentJobContext].
 */
class JobContext(
    val cacheDir: String,
    val projectDir: String,
    val env: Map<String, String> = mapOf(),
    val executor: CommandExecutor,
    val workingDir: String,
    private val cleanupActions: MutableList<suspend () -> Unit> = mutableListOf()
) : CoroutineContext.Element {

    private val logger = LoggerFactory.getLogger(JobContext::class.java)

    companion object Key : CoroutineContext.Key<JobContext>

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
     * @throws dev.kannich.stdlib.JobFailedException if the directory does not exist
     */
    suspend fun <T> cd(path: String, block: suspend () -> T): T {
        val newWorkingDir = "$workingDir/$path"

        // Check if directory exists before changing to it
        val checkResult = executor.exec(
            command = listOf("test", "-d", newWorkingDir),
            workingDir = workingDir,
            env = env,
            silent = true
        )
        if (!checkResult.success) {
            fail("Directory '$path' does not exist (full path: $newWorkingDir)")
        }

        val newCtx = JobContext(
            cacheDir = cacheDir,
            projectDir = projectDir,
            env = env,
            executor = executor,
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
            cacheDir = cacheDir,
            projectDir = projectDir,
            env = newEnv,
            executor = executor,
            workingDir = workingDir,
            cleanupActions = cleanupActions
        )

        return withContext(newCtx) {
            block()
        }
    }

    /**
     * Runs all registered cleanup actions in reverse order.
     * Exceptions during cleanup are logged but don't prevent other cleanups from running.
     * Used by ExecutionEngine after job execution.
     */
    suspend fun runCleanup() {
        cleanupActions.asReversed().forEach { action ->
            runCatching { action() }.onFailure { e ->
                logger.warn("Cleanup action failed: ${e.message}")
            }
        }
    }
}

/**
 * Gets the current job context.
 * @throws IllegalStateException if no context is available
 */
suspend fun currentJobContext(): JobContext =
    currentCoroutineContext()[JobContext]
        ?: error("No JobContext available. Are you inside a job block during execution?")
