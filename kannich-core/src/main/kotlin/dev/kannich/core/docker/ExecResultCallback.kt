package dev.kannich.core.docker

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.CountDownLatch

/**
 * Callback for capturing docker exec output.
 * - Logs output in real-time via SLF4J (stdout = info, stderr = error)
 * - Collects stdout and stderr separately for programmatic use
 *
 * @param silent If true, don't log output (for internal commands)
 */
class ExecResultCallback(
    private val silent: Boolean = false
) : ResultCallback<Frame> {

    private val logger = LoggerFactory.getLogger(ExecResultCallback::class.java)
    private val latch = CountDownLatch(1)
    private var closeable: Closeable? = null

    private val stdoutBuilder = StringBuilder()
    private val stderrBuilder = StringBuilder()

    override fun onStart(closeable: Closeable?) {
        this.closeable = closeable
    }

    override fun onNext(frame: Frame?) {
        frame?.let {
            val payload = String(it.payload).trimEnd('\n', '\r')
            if (payload.isNotEmpty()) {
                when (it.streamType) {
                    StreamType.STDERR -> {
                        stderrBuilder.appendLine(payload)
                        if (!silent) {
                            logger.error(payload)
                        }
                    }
                    else -> {
                        stdoutBuilder.appendLine(payload)
                        if (!silent) {
                            logger.info(payload)
                        }
                    }
                }
            }
        }
    }

    override fun onError(throwable: Throwable?) {
        throwable?.let {
            val errorMessage = it.message ?: "Unknown error"
            stderrBuilder.appendLine(errorMessage)
            if (!silent) {
                logger.error(errorMessage)
            }
        }
        latch.countDown()
    }

    override fun onComplete() {
        latch.countDown()
    }

    override fun close() {
        closeable?.close()
    }

    fun awaitCompletion(): ExecResultCallback {
        latch.await()
        return this
    }

    /**
     * Get collected stdout (available after awaitCompletion).
     */
    val stdout: String get() = stdoutBuilder.toString().trimEnd()

    /**
     * Get collected stderr (available after awaitCompletion).
     */
    val stderr: String get() = stderrBuilder.toString().trimEnd()
}
