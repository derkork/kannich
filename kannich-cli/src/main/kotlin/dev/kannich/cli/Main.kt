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
import dev.kannich.core.Version
import dev.kannich.core.dsl.KannichScriptHost
import dev.kannich.core.execution.ExecutionEngine
import dev.kannich.stdlib.*
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
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
    private val devMode by option("--dev-mode", "-d", help = "Use host Maven repository for extension development")
        .flag()
    private val list by option("--list", "-l", help = "List the contents of the kannichfile")
        .flag()

    override fun run() {
        configureLogging()
        logger.info("Kannich ${Version.VERSION}")

        if (execution == null && !list) {
            logger.info("Usage: kannich [OPTIONS] <execution>")
            logger.error("No execution specified. Use 'kannich <execution>' to run a pipeline.")
            return
        }

        val projectDir = File(".").absoluteFile
        val scriptFile = File(projectDir, kannichFile)

        if (!scriptFile.exists()) {
            logger.error("Script file not found: $scriptFile")
            exitProcess(1)
        }

        // Setup Maven repository symlink (normal mode: cached, dev mode: host .m2)
        setupMavenRepository()

        // Parse -e KEY=VALUE arguments into a map (split at first =)
        // This is done before script evaluation so getEnv() in builders can see these values
        val extraEnv = envVars.mapNotNull { envVar ->
            val idx = envVar.indexOf('=')
            if (idx > 0) {
                val key = envVar.take(idx)
                val value = envVar.substring(idx + 1)
                key to value
            } else {
                logger.warn("Invalid environment variable format (expected KEY=VALUE): $envVar")
                null
            }
        }.toMap()

        // Set extra env vars for pipeline definition (see PipelineEnv for why)
        PipelineEnv.setExtraEnv(extraEnv)

        // Parse the script
        logger.info("Loading pipeline from $kannichFile...")
        val scriptHost = KannichScriptHost()
        val result = scriptHost.evaluate(scriptFile)

        // Clear extra env vars after evaluation
        PipelineEnv.clear()

        val pipeline = result.getOrElse { error ->
            logger.error("Error loading pipeline: ${error.message}")
            exitProcess(1)
        }

        if (pipeline !is Pipeline) {
            logger.error("Script did not return a Pipeline")
            exitProcess(1)
        }

        logger.info("Pipeline loaded.")

        if (list) {
            listPipeline(pipeline, execution)
            return
        }

        // Check if requested execution exists
        if (!pipeline.executions.containsKey(execution)) {
            logger.error("Execution '$execution' not found")
            logger.info("Available executions: ${pipeline.executions.keys.joinToString()}")
            exitProcess(1)
        }

        // Run the execution
        logger.info("Running execution: $execution")

        val executionEngine = ExecutionEngine(Path.of(artifactsDir), extraEnv)

        try {
            val executionResult = executionEngine.runExecution(pipeline, execution!!)

            if (executionResult) {
                logger.info("Execution completed successfully.")
            } else {
                logger.error("Execution failed.")
                exitProcess(1)
            }
        } catch (e: Exception) {
            logger.error("Error during execution: ${e.message}")
            exitProcess(1)
        }
    }

    private fun setupMavenRepository() {
        val m2Dir = File("/root/.m2")
        m2Dir.mkdirs()

        val target = if (devMode) {
            val devRepo = File("/kannich/dev-repo")
            if (!devRepo.exists()) {
                logger.error("Dev mode requires host .m2/repository to be mounted.")
                logger.error("Ensure ~/.m2/repository exists on host.")
                exitProcess(1)
            }
            logger.info("Dev mode: using host Maven repository")
            devRepo.toPath()
        } else {
            val cacheRepo = File("/kannich/cache/kannich-deps")
            cacheRepo.mkdirs()
            cacheRepo.toPath()
        }

        val repoDir = m2Dir.resolve("repository")
        Files.createSymbolicLink(repoDir.toPath(), target)
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

    private fun listPipeline(pipeline: Pipeline, targetExecution: String?) {
        val executionsToList = if (targetExecution != null) {
            val exec = pipeline.executions[targetExecution]
            if (exec == null) {
                logger.error("Execution '$targetExecution' not found")
                exitProcess(1)
            }
            listOf(exec)
        } else {
            pipeline.executions.values.toList()
        }

        logger.info("Pipeline contents:")
        executionsToList.forEach { exec ->
            val desc = if (!exec.description.isNullOrBlank()) " - ${exec.description}" else ""
            logger.info("[E] ${exec.name}$desc")
            printSteps(exec.steps, "  ")
        }
    }

    private fun printSteps(steps: List<ExecutionStep>, indent: String, inParallel: Boolean = false) {
        steps.forEach { step ->
            val prefix = if (inParallel) "| - " else "- "
            when (step) {
                is JobExecutionStep -> {
                    val job = step.job
                    val desc = if (!job.description.isNullOrBlank()) " - ${job.description}" else ""
                    logger.info("$indent$prefix[J] ${job.name}$desc")
                }
                is ExecutionReference -> {
                    val exec = step.execution
                    val desc = if (!exec.description.isNullOrBlank()) " - ${exec.description}" else ""
                    logger.info("$indent$prefix[E] ${exec.name}$desc")
                    printSteps(exec.steps, indent + if (inParallel) "|   " else "  ")
                }
                is SequentialSteps -> {
                    printSteps(step.steps, indent, inParallel)
                }
                is ParallelSteps -> {
                    printSteps(step.steps, indent, true)
                }
            }
        }
    }
}

fun main(args: Array<String>) = KannichCommand().main(args)
