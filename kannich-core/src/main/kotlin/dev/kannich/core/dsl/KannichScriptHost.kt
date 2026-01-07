package dev.kannich.core.dsl

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

/**
 * Compiles and evaluates Kannich script files (.kannichfile.main.kts).
 * Handles Maven dependency resolution.
 */
class KannichScriptHost {
    private val logger: Logger = LoggerFactory.getLogger(KannichScriptHost::class.java)

    private val scriptingHost = BasicJvmScriptingHost()

    /**
     * Evaluates a .kannichfile and returns the result.
     * The script's last expression should be a pipeline {} call.
     * Caller should cast the result to dev.kannich.stdlib.Pipeline.
     */
    fun evaluate(scriptFile: File): Result<Any> {
        require(scriptFile.exists()) { "Script file not found: $scriptFile" }

        val compilationConfig = createJvmCompilationConfigurationFromTemplate<KannichScript>()

        val result = scriptingHost.eval(
            scriptFile.toScriptSource(),
            compilationConfig,
            null
        )

        return extractResult(result)
    }

    private fun extractResult(result: ResultWithDiagnostics<EvaluationResult>): Result<Any> {
        return when (result) {
            is ResultWithDiagnostics.Success -> {
                val returnValue = result.value.returnValue
                when (returnValue) {
                    is ResultValue.Value -> {
                        returnValue.value?.let { Result.success(it) }
                            ?: Result.failure(IllegalStateException("Script returned null"))
                    }
                    is ResultValue.Unit -> {
                        Result.failure(IllegalStateException(
                            "Script must end with a pipeline {} call"
                        ))
                    }
                    is ResultValue.Error -> {
                        Result.failure(returnValue.error)
                    }
                    else -> {
                        Result.failure(IllegalStateException("Unexpected result: $returnValue"))
                    }
                }
            }
            is ResultWithDiagnostics.Failure -> {
                for (report in result.reports) {
                    val location = report.location
                    val exception = report.exception
                    if (location != null) {
                        logger.error("line ${location.start.line} col ${location.start.col}: ${report.message}")
                    }
                    else if (exception != null) {
                        // get full exception with stacktrace
                        val writer = StringWriter()
                        report.exception?.printStackTrace(PrintWriter(writer))
                        logger.error(writer.toString())
                    }
                    else {
                        logger.error("Unexpected error: ${report.message}")
                    }
                }
                logger.error("got ${result.reports.size} error(s).")

                Result.failure(ScriptEvaluationException("Script evaluation failed"))
            }
        }
    }
}

class ScriptEvaluationException(message: String) : Exception(message)
