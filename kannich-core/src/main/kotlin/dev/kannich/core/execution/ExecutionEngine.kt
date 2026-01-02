package dev.kannich.core.execution

import dev.kannich.core.docker.ContainerManager
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
import dev.kannich.stdlib.context.JobExecutionContext
import dev.kannich.stdlib.context.PipelineContext
import dev.kannich.stdlib.timed
import dev.kannich.stdlib.tools.EnvTool
import dev.kannich.stdlib.tools.FsTool
import dev.kannich.stdlib.tools.JobEnvContext
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.coroutines.CoroutineContext

/**
 * Executes Kannich pipelines inside Docker containers.
 * Handles job orchestration, overlay isolation, and artifact collection.
 */
class ExecutionEngine(
    private val containerManager: ContainerManager,
    private val artifactsDir: File,
    private val extraEnv: Map<String, String> = emptyMap()
) {
    private val logger = LoggerFactory.getLogger(ExecutionEngine::class.java)
    private val jobResults = mutableMapOf<String, JobResult>()

    /**
     * Runs a named execution from the pipeline.
     */
    fun runExecution(pipeline: Pipeline, executionName: String): ExecutionResult {
        val execution = pipeline.executions[executionName]
            ?: throw IllegalArgumentException("Execution not found: $executionName")

        logger.info("Starting execution: $executionName")
        containerManager.initialize()

        return try {
            executeSteps(execution.steps, pipeline)
        } finally {
            logger.info("Execution finished: $executionName")
            containerManager.close()
        }
    }

    private fun executeSteps(
        steps: List<ExecutionStep>,
        pipeline: Pipeline,
        parentLayerId: String? = null
    ): ExecutionResult {
        var currentLayerId = parentLayerId
        val layersToCleanup = mutableListOf<String>()

        try {
            for (step in steps) {
                val result = when (step) {
                    is JobExecutionStep -> {
                        val (execResult, newLayerId) = executeJob(step.job, currentLayerId)
                        if (execResult.success && newLayerId != null) {
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

                if (!result.success) {
                    return result
                }
            }

            return ExecutionResult(
                success = true,
                jobResults = jobResults.toMap()
            )
        } finally {
            // Cleanup intermediate layers
            layersToCleanup.forEach { containerManager.removeJobLayer(it) }
            // Cleanup final layer
            currentLayerId?.let { containerManager.removeJobLayer(it) }
        }
    }

    private fun executeSequential(
        steps: List<ExecutionStep>,
        pipeline: Pipeline,
        parentLayerId: String? = null
    ): ExecutionResult {
        var currentLayerId = parentLayerId
        val layersToCleanup = mutableListOf<String>()

        try {
            for (step in steps) {
                val result = when (step) {
                    is JobExecutionStep -> {
                        val (execResult, newLayerId) = executeJob(step.job, currentLayerId)
                        if (execResult.success && newLayerId != null) {
                            currentLayerId?.let { layersToCleanup.add(it) }
                            currentLayerId = newLayerId
                        }
                        execResult
                    }

                    is ExecutionReference -> executeSteps(step.execution.steps, pipeline, currentLayerId)
                    is SequentialSteps -> executeSequential(step.steps, pipeline, currentLayerId)
                    is ParallelSteps -> executeParallel(step.steps, pipeline, currentLayerId)
                }

                if (!result.success) {
                    return result
                }
            }
            return ExecutionResult(success = true, jobResults = jobResults.toMap())
        } finally {
            layersToCleanup.forEach { containerManager.removeJobLayer(it) }
            currentLayerId?.let { containerManager.removeJobLayer(it) }
        }
    }

    private fun executeParallel(
        steps: List<ExecutionStep>,
        pipeline: Pipeline,
        parentLayerId: String? = null
    ): ExecutionResult {
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

        val failed = results.any { !it.success }
        return ExecutionResult(success = !failed, jobResults = jobResults.toMap())
    }

    /**
     * Executes a job in its own layer.
     * Returns the execution result and the layer ID (for chaining to subsequent jobs).
     *
     * Uses coroutine context elements to ensure context is properly propagated
     * across suspension points when running in parallel.
     */
    private fun executeJob(job: Job, parentLayerId: String? = null): Pair<ExecutionResult, String?> {
        logger.info("Running job: ${job.name}")

        // Create job layer from parent (or workspace if no parent)
        val layerId = containerManager.createJobLayer(parentLayerId)
        val workDir = containerManager.getLayerWorkDir(layerId)

        // Create execution context - paths come from ContainerManager
        // Merge system env with extra env (extra env takes precedence)
        val pipelineCtx = PipelineContext(
            cacheDir = containerManager.containerCacheDir,
            projectDir = containerManager.containerProjectDir,
            env = System.getenv() + extraEnv
        )
        val executor = ContainerCommandExecutor(containerManager, workDir)
        val jobCtx = JobExecutionContext(pipelineCtx, executor, workDir)

        // Create job environment context for this job
        val jobEnvCtx = EnvTool.createJobEnvContext()

        // Build coroutine context with all context elements
        // This ensures proper ThreadLocal propagation across coroutine suspensions
        val coroutineContext: CoroutineContext = pipelineCtx + jobCtx + jobEnvCtx

        // Execute job block with context
        var success = true
        var output = ""

        try {
            // Use runBlocking with context elements to ensure proper propagation
            // across any suspension points in the job execution
            runBlocking(coroutineContext) {
                JobExecutionContext.withContext(jobCtx) {
                    val scopeResult = timed("Job ${job.name}") {
                        JobScope.withScope(job.name) { job.block(this) }
                    }

                    // Collect artifacts while still in context (so FsTool works)
                    if (scopeResult.artifacts.isNotEmpty()) {
                        timed("Collecting artifacts") {
                            for (spec in scopeResult.artifacts) {
                                collectArtifacts(spec, workDir)
                            }
                        }
                    }
                }
            }
        } catch (e: JobFailedException) {
            logger.warn("Job failed: ${e.message}")
            success = false
            output = e.message ?: "Job failed"
        } catch (e: Exception) {
            logger.error("Unexpected error in job: ${e.message}", e)
            success = false
            output = "Unexpected error: ${e.message}"
        }

        val result = JobResult(
            name = job.name,
            success = success,
            output = output
        )
        jobResults[job.name] = result

        val execResult = ExecutionResult(success = success, jobResults = mapOf(job.name to result))

        // Return layer ID only on success (for chaining), cleanup handled by caller
        return if (success) {
            Pair(execResult, layerId)
        } else {
            containerManager.removeJobLayer(layerId)
            Pair(execResult, null)
        }
    }

    /**
     * Collects artifacts from the container matching the given specification.
     * Uses FsTool.glob() for pattern matching (runs within JobExecutionContext).
     */
    private fun collectArtifacts(spec: ArtifactSpec, workDir: String) {
        val fs = FsTool()
        val matchingPaths = fs.glob(spec.includes, spec.excludes, workDir)

        if (matchingPaths.isEmpty()) {
            logger.info("No artifacts matched the patterns")
            return
        }

        // Security check: ensure all destinations are within artifacts dir
        val safePaths = matchingPaths.filter { relativePath ->
            val destFile = File(artifactsDir, relativePath)
            val isSafe = destFile.canonicalPath.startsWith(artifactsDir.canonicalPath)
            if (!isSafe) {
                logger.warn("Skipping artifact outside workspace: $relativePath")
            }
            isSafe
        }

        if (safePaths.isEmpty()) {
            return
        }

        // Log matched artifacts
        for (path in safePaths) {
            logger.info("Matched artifact: $path")
        }

        // Copy all artifacts
        containerManager.copyArtifacts(workDir, safePaths, artifactsDir)
    }
}

data class ExecutionResult(
    val success: Boolean,
    val jobResults: Map<String, JobResult>
)

data class JobResult(
    val name: String,
    val success: Boolean,
    val output: String
)
