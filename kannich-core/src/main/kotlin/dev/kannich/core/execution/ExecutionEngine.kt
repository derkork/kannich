package dev.kannich.core.execution

import dev.kannich.stdlib.ArtifactSpec
import dev.kannich.stdlib.ExecutionReference
import dev.kannich.stdlib.ExecutionStep
import dev.kannich.stdlib.Job
import dev.kannich.stdlib.JobExecutionStep
import dev.kannich.stdlib.JobFailedException
import dev.kannich.stdlib.JobScope
import dev.kannich.stdlib.ParallelSteps
import dev.kannich.stdlib.Pipeline
import dev.kannich.stdlib.SequentialSteps
import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.timedSuspend
import dev.kannich.stdlib.tools.Fs
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createParentDirectories

/**
 * Executes Kannich pipelines inside Docker containers.
 * Handles job orchestration, overlay isolation, and artifact collection.
 */
class ExecutionEngine(
    private val artifactsDir: Path,
    private val extraEnv: Map<String, String> = emptyMap()
) {
    private val logger = LoggerFactory.getLogger(ExecutionEngine::class.java)

    /**
     * Runs a named execution from the pipeline.
     */
    fun runExecution(pipeline: Pipeline, executionName: String): Boolean {
        val execution = pipeline.executions[executionName]
            ?: throw IllegalArgumentException("Execution not found: $executionName")


        return executeSteps(execution.steps, pipeline)
    }

    private fun executeSteps(
        steps: List<ExecutionStep>,
        pipeline: Pipeline,
        parentLayerId: String? = null
    ): Boolean {
        var currentLayerId = parentLayerId
        val layersToCleanup = mutableListOf<String>()

        try {
            for (step in steps) {
                val result = when (step) {
                    is JobExecutionStep -> {
                        val (execResult, newLayerId) = executeJob(step.job, currentLayerId)
                        if (execResult && newLayerId != null) {
                            // Track old layer for cleanup, use new layer for next job
                            currentLayerId?.let { layersToCleanup.add(it) }
                            currentLayerId = newLayerId
                        }
                        execResult
                    }

                    is ExecutionReference -> {
                        val refExecution = step.execution
                        executeSteps(refExecution.steps, pipeline, currentLayerId)
                    }

                    is SequentialSteps -> executeSequential(step.steps, pipeline, currentLayerId)
                    is ParallelSteps -> executeParallel(step.steps, pipeline, currentLayerId)
                }

                if (!result) {
                    return result
                }
            }

            return true
        } finally {
            // Cleanup intermediate layers
            layersToCleanup.forEach { LayerManager.removeJobLayer(it) }
            // Cleanup final layer
            currentLayerId?.let { LayerManager.removeJobLayer(it) }
        }
    }

    private fun executeSequential(
        steps: List<ExecutionStep>,
        pipeline: Pipeline,
        parentLayerId: String? = null
    ): Boolean {
        var currentLayerId = parentLayerId
        val layersToCleanup = mutableListOf<String>()

        try {
            for (step in steps) {
                val result = when (step) {
                    is JobExecutionStep -> {
                        val (execResult, newLayerId) = executeJob(step.job, currentLayerId)
                        if (execResult && newLayerId != null) {
                            currentLayerId?.let { layersToCleanup.add(it) }
                            currentLayerId = newLayerId
                        }
                        execResult
                    }

                    is ExecutionReference -> executeSteps(step.execution.steps, pipeline, currentLayerId)
                    is SequentialSteps -> executeSequential(step.steps, pipeline, currentLayerId)
                    is ParallelSteps -> executeParallel(step.steps, pipeline, currentLayerId)
                }

                if (!result) {
                    return result
                }
            }
            return true
        } finally {
            layersToCleanup.forEach { LayerManager.removeJobLayer(it) }
            currentLayerId?.let { LayerManager.removeJobLayer(it) }
        }
    }

    private fun executeParallel(
        steps: List<ExecutionStep>,
        pipeline: Pipeline,
        parentLayerId: String? = null
    ): Boolean {
        // Parallel jobs each get their own layer branching from the same parent
        val results = runBlocking {
            steps.map { step ->
                async(Dispatchers.IO) {
                    when (step) {
                        is JobExecutionStep -> executeJob(step.job, parentLayerId).first
                        is ExecutionReference -> executeSteps(step.execution.steps, pipeline, parentLayerId)
                        is SequentialSteps -> executeSequential(step.steps, pipeline, parentLayerId)
                        is ParallelSteps -> executeParallel(step.steps, pipeline, parentLayerId)
                    }
                }
            }.awaitAll()
        }
        // success if all children succeeded
        return results.all { it }
    }

    /**
     * Executes a job in its own layer.
     * Returns the execution result and the layer ID (for chaining to following jobs).
     */
    private fun executeJob(job: Job, parentLayerId: String? = null): Pair<Boolean, String?> {
        logger.info("Running job: ${job.name}")

        // Create job layer from parent (or workspace if no parent)
        val layerId = LayerManager.createJobLayer(parentLayerId)
        val workDir = LayerManager.getLayerWorkDir(layerId)

        // Create job context
        val jobCtx = JobContext(
            env = System.getenv() + extraEnv,
            workingDir = workDir
        )


        // Execute job block with context
        var success = true
        val scope = JobScope(job.name)

        try {
            // Context is automatically available via currentJobContext() in suspend functions
            runBlocking(jobCtx) {
                timedSuspend("Job ${job.name}") {
                    job.block(scope)
                }

                // Collect artifacts while still in context (so Fs.glob works)
                val artifacts = scope.getArtifactSpecs()
                if (artifacts.isNotEmpty()) {
                    timedSuspend("Collecting artifacts") {
                        for (spec in artifacts) {
                            collectArtifacts(spec, workDir)
                        }
                    }
                }
            }
        } catch (e: JobFailedException) {
            logger.warn("Job failed: ${e.message}")
            success = false
        } catch (e: Exception) {
            logger.error("Unexpected error in job: ${e.message}", e)
            success = false
        } finally {
            // Run cleanup actions regardless of job success/failure
            runBlocking(jobCtx) {
                jobCtx.runCleanup()
            }
        }

        // Return layer ID only on success (for chaining), cleanup handled by caller
        return if (success) {
            Pair(true, layerId)
        } else {
            LayerManager.removeJobLayer(layerId)
            Pair(false, null)
        }
    }

    /**
     * Collects artifacts from the container matching the given specification.
     * Uses Fs.glob() for pattern matching (runs within JobExecutionContext).
     */
    private suspend fun collectArtifacts(spec: ArtifactSpec, workDir: String) {
        val matchingPaths = Fs.glob(spec.includes, spec.excludes, workDir)

        if (matchingPaths.isEmpty()) {
            logger.info("No artifacts matched the patterns")
            return
        }

        // Security check: ensure all destinations are within artifacts dir
        val safePaths = matchingPaths.filter { relativePath ->
            val destFile = artifactsDir.resolve(relativePath)
            val isSafe = destFile.normalize().startsWith(artifactsDir.normalize())
            if (!isSafe) {
                logger.warn("Skipping artifact outside workspace: $relativePath")
            }
            isSafe
        }

        if (safePaths.isEmpty()) {
            return
        }

        // Log and copy matched artifacts
        for (path in safePaths) {
            logger.info("Matched artifact: $path")
            val target = artifactsDir.resolve(path)
            target.createParentDirectories()
            Files.copy(Path.of(workDir, path), target)
        }

    }
}

data class ExecutionResult(
    val success: Boolean
)

data class JobResult(
    val success: Boolean
)
