package dev.kannich.test

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import java.io.Closeable
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class ContainerExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val success: Boolean get() = exitCode == 0

    fun assertSuccess(message: String = "Command failed") {
        if (!success) {
            throw AssertionError("$message: exitCode=$exitCode, stderr=$stderr")
        }
    }
}

class ContainerExecutor(private val container: GenericContainer<*>) {

    private val logger = LoggerFactory.getLogger(ContainerExecutor::class.java)

    companion object {
        const val KANNICH_FILE_PATH = "/workspace/.kannichfile.main.kts"
    }

    fun exec(vararg cmd: String, silent: Boolean = false): ContainerExecResult {
        val dockerClient = container.dockerClient
        val containerId = container.containerId

        if (!silent) {
            logger.info("Executing: {}", cmd.joinToString(" "))
        } else {
            logger.debug("Executing: {}", cmd.joinToString(" "))
        }

        val execCreate = dockerClient.execCreateCmd(containerId)
            .withAttachStdout(true)
            .withAttachStderr(true)
            .withCmd(*cmd)
            .exec()

        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val latch = CountDownLatch(1)

        dockerClient.execStartCmd(execCreate.id)
            .exec(object : ResultCallback<Frame> {
                override fun onStart(closeable: Closeable?) {}

                override fun onNext(frame: Frame) {
                    val payload = String(frame.payload).trimEnd('\n', '\r')
                    if (payload.isNotEmpty()) {
                        when (frame.streamType) {
                            StreamType.STDOUT -> {
                                if (!silent) logger.info("[container] {}", payload) else logger.debug("[container] {}", payload)
                                stdout.appendLine(payload)
                            }
                            StreamType.STDERR -> {
                                if (!silent) logger.warn("[container] {}", payload) else logger.debug("[container] {}", payload)
                                stderr.appendLine(payload)
                            }
                            else -> {}
                        }
                    }
                }

                override fun onError(throwable: Throwable) {
                    logger.error("Exec error", throwable)
                    latch.countDown()
                }

                override fun onComplete() {
                    latch.countDown()
                }

                override fun close() {
                    latch.countDown()
                }
            })

        latch.await(10, TimeUnit.MINUTES)

        val exitCode = dockerClient.inspectExecCmd(execCreate.id).exec().exitCodeLong?.toInt() ?: -1

        return ContainerExecResult(exitCode, stdout.toString(), stderr.toString())
    }

    fun execShell(cmd: String, silent: Boolean = false): ContainerExecResult = exec("sh", "-c", cmd, silent = silent)

    /**
     * Writes the pipeline to /workspace/.kannichfile.main.kts and runs kannich.
     * This is the main entry point for running integration tests.
     *
     * @param pipeline The pipeline builder (will be built automatically)
     * @param execution The execution name to run (default: "test")
     */
    fun run(pipeline: PipelineBuilder, execution: String = "test"): ContainerExecResult {
        writeFile(KANNICH_FILE_PATH, pipeline.build())
        return runKannich(execution)
    }

    /**
     * Runs kannich with the specified execution.
     *
     * @param execution The execution name (default: "test")
     * @param extraArgs Additional arguments to pass to kannich
     */
    fun runKannich(execution: String = "test", vararg extraArgs: String): ContainerExecResult {
        val args = listOf(
            "/kannich/jdk/bin/java",
            "-jar",
            "/kannich/kannich-cli.jar",
            execution
        ) + extraArgs.toList()
        return exec(*args.toTypedArray())
    }

    fun writeFile(path: String, content: String) {
        val encoded = Base64.getEncoder().encodeToString(content.toByteArray())
        execShell("echo '$encoded' | base64 -d > $path", silent = true)
    }

    fun readFile(path: String): String = exec("cat", path, silent = true).stdout

    fun fileExists(path: String): Boolean = exec("test", "-e", path, silent = true).success

    fun directoryExists(path: String): Boolean = exec("test", "-d", path, silent = true).success

    fun mkdir(path: String) { exec("mkdir", "-p", path, silent = true) }

    fun cleanWorkspace() {
        execShell("rm -rf /workspace/* /workspace/.[!.]* 2>/dev/null || true", silent = true)
        execShell("rm -rf /kannich/overlays/* 2>/dev/null || true", silent = true)
    }
}
