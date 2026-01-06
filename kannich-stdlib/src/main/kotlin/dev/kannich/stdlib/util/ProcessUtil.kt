package dev.kannich.stdlib.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.*

object ProcessUtil {
    private val logger: Logger = LoggerFactory.getLogger(ProcessUtil::class.java)

    /**
     * Executes a command and returns the result. Output is logged as it becomes available
     * unless the silent flag is set to true.
     *
     * @param command Command and arguments to execute
     * @param workingDir Working directory inside container
     * @param env Environment variables
     * @param silent If true, don't log output (for internal commands)
     */
    fun exec(
        command: List<String>,
        workingDir: String = "/workspace",
        env: Map<String, String> = emptyMap(),
        silent: Boolean = false
    ): Result<ExecResult> = runCatching {
        if (!silent) {
            logger.info("Executing: ${command.joinToString(" ")}")
        } else {
            logger.debug("Executing: ${command.joinToString(" ")}")
        }

        val process = ProcessBuilder(command)
            .directory(File(workingDir))
            .also {
                val pbEnv = it.environment()
                pbEnv.clear()
                pbEnv.putAll(env)
            }
            .start()

        val logErr = { line: String -> logger.error(line) }
        val logInfo = { line: String -> logger.info(line) }
        val logDebug = { line: String -> logger.debug(line) }

        val stdErr = StreamGobbler(process.errorStream, if (silent) logDebug else logErr)
        val stdOut = StreamGobbler(process.inputStream, if (silent) logDebug else logInfo)
        stdErr.start()
        stdOut.start()

        val exitCode = process.waitFor()
        stdOut.join()
        stdErr.join()

        ExecResult(stdOut.toString(), stdErr.toString(), exitCode)
    }

    /**
     * Same as exec(), but executes the command as argument to `sh -c`.
     */
    fun execShell(
        command: String,
        workingDir: String = "/workspace",
        env: Map<String, String> = emptyMap(),
        silent: Boolean = false
    ): Result<ExecResult> {
        return exec(listOf("sh", "-c", command), workingDir, env, silent)
    }
}

private class StreamGobbler(val inputStream: InputStream, val onLine: (String) -> Unit = {}) : Thread() {
    private val logger: Logger = LoggerFactory.getLogger(StreamGobbler::class.java)
    private val writer: StringWriter = StringWriter()

    override fun run() {
        try {
            val br = BufferedReader(InputStreamReader(inputStream))
            while (true) {
                val line = br.readLine() ?: break
                writer.write(line)
                onLine(line)
            }
        } catch (e: Exception) {
            logger.error("Error reading from stream", e)
        }
    }

    override fun toString(): String {
        if (isAlive) {
            logger.error("StreamGobbler thread is still running")
            throw IllegalStateException("StreamGobbler thread is still running")
        }
        return writer.toString()
    }
}
