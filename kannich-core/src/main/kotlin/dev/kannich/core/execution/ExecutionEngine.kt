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
import dev.kannich.stdlib.On
import dev.kannich.stdlib.timed
import dev.kannich.stdlib.FsUtil
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.nio.file.Path

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
    fun runExecution(pipeline: Pipeline, executionName: String): Result<Unit> {
        val execution = pipeline.executions[executionName]
            ?: throw IllegalArgumentException("Execution not found: $executionName")

        // first create initial layer, as we don't modify the working copy
        val initialLayerId = LayerManager.createJobLayer().getOrElse { return Result.failure(it) }

        return executeSequential(execution.steps, initialLayerId)
    }

    private fun executeSequential(
        steps: List<ExecutionStep>,
        layerId: String
    ): Result<Unit> {
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
            if (result.isFailure) {
                return result
            }
        }
        return Result.success(Unit)
    }

    private fun executeParallel(
        steps: List<ExecutionStep>,
        parentLayerId: String,
    ): Result<Unit> {
        // for parallel steps we need to create a new layer for each child job, based on the parent layer
        // so that the parallel jobs don't interfere with each other.
        // after all jobs are done, we apply the changes to the parent layer in the order in which
        // the jobs are defined in the pipeline. afterwards we delete the parallel layers.

        val parallelLayerIds = mutableListOf<String>()
        val results = runBlocking {
            steps.map { step ->
                async(Dispatchers.IO) {
                    val layerId = LayerManager.createJobLayer(parentLayerId).getOrElse { return@async Result.failure(it) }
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
        if (!results.all { it.isSuccess }) {
            return Result.failure(Exception("Not all jobs succeeded"))
        }

        // now we find out what has changed and apply this to our original layer (e.g. we merge the changes)
        // TODO if this parallel step was the last step in the pipeline we can skip the merge
        val modifiedFiles = mutableSetOf<Path>()
        val deletedPaths = mutableSetOf<Path>()
        val targetDir = Path.of(LayerManager.getLayerWorkDir(parentLayerId))
        logger.debug("Merging parallel steps into parent layer: $parentLayerId")
        for (layerId in parallelLayerIds) {
            val sourceDir = Path.of(LayerManager.getLayerWorkDir(layerId))
            LayerManager.findLayerModifications(layerId, modifiedFiles, deletedPaths)
            logger.debug("Found ${modifiedFiles.size} modified files and ${deletedPaths.size} deleted paths in layer $layerId")
            // first delete all deleted paths from the original layer
            deletedPaths.forEach { file ->
                val targetFile = targetDir.resolve(file)
                FsUtil.delete(targetFile).getOrElse { return Result.failure(it) }
            }

            // then copy all modified files to the original layer
            modifiedFiles.forEach { file ->
                val sourceFile = sourceDir.resolve(file)
                val targetFile = targetDir.resolve(file)
                FsUtil.copy(sourceFile, targetFile).getOrElse { return Result.failure(it) }
            }
        }

        // finally, clear the parallel layers
        parallelLayerIds.forEach { LayerManager.removeJobLayer(it) }
        return Result.success(Unit)
    }

    private fun executeJob(job: Job, workDir: String): Result<Unit> {
        logger.info("Running job: ${job.name}")

        // Create job context
        val jobCtx = JobContext(
            env = System.getenv() + extraEnv,
            workingDir = workDir
        )

        var result: Result<Unit>
        // Execute job block with context
        val scope = JobScope(job.name)
        try {
            try {
                timed("Job ${job.name}") {
                    runBlocking(jobCtx) {
                        job.block(scope)
                    }
                }
                result = Result.success(Unit)
            } catch (e: JobFailedException) {
                logger.warn("Job failed: ${e.message}")
                result = Result.failure(e)
            } catch (e: Exception) {
                logger.error("Unexpected error in job: ${e.message}", e)
                result = Result.failure(e)
            }

            timed("Collecting artifacts") {
                val artifacts = scope.getArtifactSpecs()
                for (spec in artifacts) {
                    when {
                        spec.collect == On.FAILURE && result.isSuccess -> continue
                        spec.collect == On.SUCCESS && result.isFailure -> continue
                    }
                    collectArtifacts(spec, Path.of(workDir)).getOrElse { return Result.failure(it) }
                }
            }
        }
        finally {
            runBlocking(jobCtx) {
                jobCtx.close()
            }
        }

        return result
    }

    /**
     * Collects artifacts from the container matching the given specification.
     * Uses Fs.glob() for pattern matching (runs within JobExecutionContext).
     */
    private fun collectArtifacts(spec: ArtifactSpec, workDir: Path): Result<Unit> {
        val matchingPaths = FsUtil.glob(spec.includes, spec.excludes, workDir).getOrElse { return Result.failure(it) }

        if (matchingPaths.isEmpty()) {
            logger.info("No artifacts matched the patterns")
            return Result.success(Unit)
        }

        // Security check: ensure all destinations are within artifacts dir
        val safePaths = matchingPaths.filter { relativePath ->
            val sourceFile = workDir.resolve(relativePath).normalize()
            if (!sourceFile.startsWith(workDir.normalize())) {
                logger.warn("Skipping artifact outside workspace: $relativePath")
                false
            } else {
                true
            }
        }

        if (safePaths.isEmpty()) {
            return Result.success(Unit)
        }

        // Log and copy matched artifacts
        for (path in safePaths) {
            logger.info("Matched artifact: $path")
            FsUtil.copy(workDir.resolve(path), artifactsDir.resolve(path)).getOrElse { return Result.failure(it) }
        }

        return Result.success(Unit)
    }
}

