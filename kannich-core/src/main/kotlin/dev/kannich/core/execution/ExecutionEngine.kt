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
import dev.kannich.stdlib.tools.toUnixString
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createParentDirectories

/**
 * Executes Kannich pipelines. Handles job orchestration, overlay isolation, and artifact collection.
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

        // first create initial layer, as we don't modify the working copy
        val initialLayerId = LayerManager.createJobLayer()

        return executeSequential(execution.steps, initialLayerId)
    }

    private fun executeSequential(
        steps: List<ExecutionStep>,
        layerId: String
    ): Boolean {
        // sequential steps don't need new layers, they can just work on the same layer one after another
        for ((index, step) in steps.withIndex()) {
            val result = when (step) {
                // simple job execution, run it on the layer
                is JobExecutionStep -> executeJob(step.job, LayerManager.getLayerWorkDir(layerId))
                // an execution is implicitly sequential, so just run its steps
                is ExecutionReference -> executeSequential(step.execution.steps, layerId)
                // same thing for sequential steps
                is SequentialSteps -> executeSequential(step.steps, layerId)
                // and parallel steps we have to execute in parallel
                is ParallelSteps -> executeParallel(step.steps, layerId)
            }
            // break on first failure
            if (!result) {
                return false
            }
        }
        return true
    }

    private fun executeParallel(
        steps: List<ExecutionStep>,
        parentLayerId: String? = null,
    ): Boolean {
        // for parallel steps we need to create a new layer for each child job, based on the parent layer
        // so that the parallel jobs don't interfere with each other.
        // after all jobs are done, we apply the changes to the parent layer in the order in which
        // the jobs are defined in the pipeline. afterwards we delete the parallel layers.

        val parallelLayerIds = mutableListOf<String>()
        val results = runBlocking {
            steps.map { step ->
                async(Dispatchers.IO) {
                    val layerId = LayerManager.createJobLayer(parentLayerId)
                    parallelLayerIds.add(layerId)

                    // this is pretty much the same as sequential execution, but we need to pass the layer ID to the child steps
                    when (step) {
                        is JobExecutionStep -> executeJob(step.job, LayerManager.getLayerWorkDir(layerId))
                        is ExecutionReference -> executeSequential(step.execution.steps, layerId)
                        is SequentialSteps -> executeSequential(step.steps, layerId)
                        is ParallelSteps -> executeParallel(step.steps, layerId)
                    }
                }
            }.awaitAll()
        }

        // if not all jobs succeeded, return false, don't bother deleting the layers as we'll terminate soon anyways.
        if (!results.all { it }) {
            return false
        }

        // now we find out what has changed and apply this to our original layer (e.g. we merge the changes)
        // TODO implement
        // TODO if this parallel step was the last step in the pipeline we can skip the merge

        // finally, clear the parallel layers
        parallelLayerIds.forEach { LayerManager.removeJobLayer(it) }
        return true
    }

    private fun executeJob(job: Job, workDir:String): Boolean {
        logger.info("Running job: ${job.name}")

        // Create job context
        val jobCtx = JobContext(
            env = System.getenv() + extraEnv,
            workingDir = workDir
        )

        // Execute job block with context
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
            return true
        } catch (e: JobFailedException) {
            logger.warn("Job failed: ${e.message}")
            return false
        } catch (e: Exception) {
            logger.error("Unexpected error in job: ${e.message}", e)
            return false
        } finally {
            // Run cleanup actions regardless of job success/failure
            runBlocking(jobCtx) {
                jobCtx.runCleanup()
            }
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
            Fs.copy(path, artifactsDir.resolve(path).toUnixString())
        }
    }
}

