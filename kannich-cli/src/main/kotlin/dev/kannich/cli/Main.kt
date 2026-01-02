package dev.kannich.cli

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import dev.kannich.core.Kannich
import dev.kannich.core.docker.ContainerManager
import dev.kannich.core.docker.KannichDockerClient
import dev.kannich.core.dsl.KannichContext
import dev.kannich.core.dsl.KannichScriptHost
import dev.kannich.core.execution.ExecutionEngine
import dev.kannich.stdlib.Pipeline
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

class KannichCommand : CliktCommand(name = "kannich") {
    private val logger = LoggerFactory.getLogger(KannichCommand::class.java)

    private val execution by argument(help = "Execution to run").optional()
    private val artifactsDir by option("--artifacts-dir", help = "Directory to copy artifacts to")
        .default(".")
    private val kannichFile by option("--file", "-f", help = "Path to .kannichfile.main.kts")
        .default(".kannichfile.main.kts")
    private val verbose by option("--verbose", "-v", help = "Enable verbose/debug output")
        .flag()
    private val envVars by option("-e", "--env", help = "Set environment variable (KEY=VALUE)")
        .multiple()

    override fun run() {
        configureLogging()
        logger.info("Kannich ${Kannich.VERSION}")

        if (execution == null) {
            logger.info("Usage: kannich [OPTIONS] <execution>")
            logger.info("No execution specified. Use 'kannich <execution>' to run a pipeline.")
            return
        }

        val projectDir = File(".").absoluteFile
        val scriptFile = File(projectDir, kannichFile)

        if (!scriptFile.exists()) {
            logger.error("Script file not found: $scriptFile")
            exitProcess(1)
        }

        // Verify Docker is available
        val dockerClient = KannichDockerClient()
        if (!dockerClient.ping()) {
            logger.error("Docker daemon not available. Is Docker running?")
            exitProcess(1)
        }
        logger.info("Docker: ${dockerClient.version()}")

        // Parse the script
        logger.info("Loading pipeline from $kannichFile...")
        val context = KannichContext(projectDir)
        val scriptHost = KannichScriptHost()
        val result = scriptHost.evaluate(scriptFile, context)

        val pipeline = result.getOrElse { error ->
            logger.error("Error loading pipeline: ${error.message}")
            exitProcess(1)
        }

        if (pipeline !is Pipeline) {
            logger.error("Script did not return a Pipeline")
            exitProcess(1)
        }

        logger.info("Pipeline loaded: ${pipeline.jobs.size} jobs, ${pipeline.executions.size} executions")

        // Check if requested execution exists
        if (!pipeline.executions.containsKey(execution)) {
            logger.error("Execution '$execution' not found")
            logger.info("Available executions: ${pipeline.executions.keys.joinToString()}")
            exitProcess(1)
        }

        // Run the execution
        logger.info("Running execution: $execution")

        // Use host paths for Docker mounts (needed when running inside wrapper container)
        // These are already in Docker format (e.g., /d/foo/bar) so don't wrap in File
        val hostProjectPath = System.getenv("KANNICH_HOST_PROJECT_DIR")
        val hostCachePath = System.getenv("KANNICH_HOST_CACHE_DIR")

        val containerManager = ContainerManager(
            dockerClient,
            projectDir,
            context.cacheDir,
            hostProjectPath,
            hostCachePath
        )

        // Parse -e KEY=VALUE arguments into a map (split at first =)
        val extraEnv = envVars.mapNotNull { envVar ->
            val idx = envVar.indexOf('=')
            if (idx > 0) {
                val key = envVar.substring(0, idx)
                val value = envVar.substring(idx + 1)
                key to value
            } else {
                logger.warn("Invalid environment variable format (expected KEY=VALUE): $envVar")
                null
            }
        }.toMap()

        val executionEngine = ExecutionEngine(containerManager, File(artifactsDir), extraEnv)

        try {
            val executionResult = executionEngine.runExecution(pipeline, execution!!)

            if (executionResult.success) {
                logger.info("")
                logger.info("Execution completed successfully!")
            } else {
                logger.info("")
                logger.error("Execution failed!")
                exitProcess(1)
            }
        } catch (e: Exception) {
            logger.error("Error during execution: ${e.message}")
            exitProcess(1)
        }
    }

    private fun configureLogging() {
        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val kannichLogger = LoggerFactory.getLogger("dev.kannich") as Logger

        // Configure verbose mode on our custom appender
        val appender: Appender<ILoggingEvent>? = rootLogger.getAppender("COLORED")
        if (appender is ColoredConsoleAppender) {
            appender.verbose = verbose
        }

        // Set log level based on verbose flag
        if (verbose) {
            rootLogger.level = Level.DEBUG
            kannichLogger.level = Level.DEBUG
        }
    }
}

fun main(args: Array<String>) = KannichCommand().main(args)
