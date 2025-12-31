package dev.kannich.core.execution

import dev.kannich.core.docker.ContainerManager
import dev.kannich.core.util.AntPathMatcher
import dev.kannich.stdlib.ArtifactSpec
import dev.kannich.stdlib.ExecutionReference
import dev.kannich.stdlib.ExecutionStep
import dev.kannich.stdlib.Job
import dev.kannich.stdlib.JobExecutionStep
import dev.kannich.stdlib.JobFailedException
import dev.kannich.stdlib.JobScope
import dev.kannich.stdlib.JobScopeResult
import dev.kannich.stdlib.ParallelSteps
import dev.kannich.stdlib.Pipeline
import dev.kannich.stdlib.SequentialSteps
import dev.kannich.stdlib.context.JobExecutionContext
import dev.kannich.stdlib.context.PipelineContext
import dev.kannich.stdlib.timed
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Executes Kannich pipelines inside Docker containers.
 * Handles job orchestration, overlay isolation, and artifact collection.
 */
class ExecutionEngine(
    private val containerManager: ContainerManager,
    private val artifactsDir: File
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
            val result = executeSteps(execution.steps, pipeline)
            logger.info("Execution completed: $executionName (success=${result.success})")
            result
        } finally {
            containerManager.close()
        }
    }

    private fun executeSteps(steps: List<ExecutionStep>, pipeline: Pipeline, parentLayerId: String? = null): ExecutionResult {
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

    private fun executeSequential(steps: List<ExecutionStep>, pipeline: Pipeline, parentLayerId: String? = null): ExecutionResult {
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

    private fun executeParallel(steps: List<ExecutionStep>, pipeline: Pipeline, parentLayerId: String? = null): ExecutionResult {
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
     */
    private fun executeJob(job: Job, parentLayerId: String? = null): Pair<ExecutionResult, String?> {
        logger.info("Running job: ${job.name}")

        // Create job layer from parent (or workspace if no parent)
        val layerId = containerManager.createJobLayer(parentLayerId)
        val workDir = containerManager.getLayerWorkDir(layerId)

        // Create execution context - paths come from ContainerManager
        val pipelineCtx = PipelineContext(
            cacheDir = containerManager.containerCacheDir,
            projectDir = containerManager.containerProjectDir,
            env = System.getenv()
        )
        val executor = ContainerCommandExecutor(containerManager, workDir)
        val jobCtx = JobExecutionContext(pipelineCtx, executor, workDir)

        // Execute job block with context
        var success = true
        var output = ""
        var artifactSpecs: List<ArtifactSpec> = emptyList()

        try {
            timed("Job ${job.name}") {
                JobExecutionContext.withContext(jobCtx) {
                    val scopeResult = JobScope.withScope { job.block(this) }
                    artifactSpecs = scopeResult.artifacts
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

        // Collect artifacts if job succeeded and artifacts were specified
        if (success && artifactSpecs.isNotEmpty()) {
            timed("Collecting artifacts for ${job.name}") {
                for (spec in artifactSpecs) {
                    collectArtifacts(spec, workDir, layerId)
                }
            }
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
     *
     * Uses ant-style glob pattern matching:
     * - `*` matches 0 or more characters except `/`
     * - `**` matches 0 or more characters including `/`
     * - `?` matches exactly one character
     *
     * Only files within the workspace directory can be collected as artifacts.
     */
    private fun collectArtifacts(spec: ArtifactSpec, workDir: String, layerId: String) {
        // List all files recursively in the workspace (files and directories)
        val findResult = containerManager.execShell(
            "find $workDir \\( -type f -o -type d \\) 2>/dev/null || true",
            workingDir = workDir,
            silent = true
        )

        val allPaths = findResult.stdout.lines()
            .filter { it.isNotBlank() && it != workDir }
            .map { it.removePrefix("$workDir/") }

        // Find paths matching include patterns but not exclude patterns
        val matchingPaths = allPaths.filter { relativePath ->
            val matchesInclude = AntPathMatcher.matchesAny(spec.includes, relativePath)
            val matchesExclude = spec.excludes.isNotEmpty() &&
                                 AntPathMatcher.matchesAny(spec.excludes, relativePath)
            matchesInclude && !matchesExclude
        }

        // Copy matching artifacts
        for (relativePath in matchingPaths) {
            val containerPath = "$workDir/$relativePath"
            val destFile = File(artifactsDir, relativePath)

            // Security check: ensure destination is within artifacts dir
            if (!destFile.canonicalPath.startsWith(artifactsDir.canonicalPath)) {
                logger.warn("Skipping artifact outside workspace: $relativePath")
                continue
            }

            destFile.parentFile?.mkdirs()
            containerManager.copyArtifacts(containerPath, destFile.parentFile)
            logger.info("Collected artifact: $relativePath")
        }
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
