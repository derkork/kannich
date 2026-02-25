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
import dev.kannich.core.proxy.ProxyConfiguration
import dev.kannich.stdlib.*
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.system.exitProcess

class KannichCommand : CliktCommand(name = "kannich") {
    private val logger = LoggerFactory.getLogger(KannichCommand::class.java)

    private val execution by argument(help = "Execution to run").optional()
    private val kannichFile by option("--file", "-f", help = "Path to .kannichfile.main.kts")
        .default(".kannichfile.main.kts")
    private val verbose by option("--verbose", "-v", help = "Enable verbose/debug output")
        .flag()
    private val envVars by option("--env", "-e", help = "Set environment variable (KEY=VALUE)")
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

        val finalEnv = buildEnvironment()
        DefaultEnv.env = finalEnv

        val settingsXml = finalEnv["KANNICH_BOOTSTRAP_SETTINGS_XML"] ?: ""

        setupMavenRepository(settingsXml)


        // Configure proxy for Maven dependency resolution
        ProxyConfiguration.configureFromEnvironment(finalEnv)

        // Parse the script
        logger.info("Loading pipeline from $kannichFile...")
        val scriptHost = KannichScriptHost()
        val result = scriptHost.evaluate(scriptFile)

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
            listPipeline(pipeline)
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

        val executionEngine = ExecutionEngine(Path.of(Kannich.WORKSPACE_DIR).absolute(), finalEnv)

        executionEngine.runExecution(pipeline, execution!!).getOrElse {
            logger.error("Execution failed: ${it.message}")
            exitProcess(1)
        }
    }

    private fun buildEnvironment(): Map<String, String> {
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

        val hostEnv = determineHostEnvVars()
        val finalEnv = hostEnv + extraEnv
        logger.debug("Using env vars: ${finalEnv.keys.joinToString(", ")}")
        return finalEnv
    }

    private fun determineHostEnvVars(): Map<String, String> {
        // read the current env from /workspace/.kannich_current_env (this line based VARIABLE=VALUE). Can be
        // terminated by LF or CRLF depending on the host OS.
        val currentEnvFile = Path.of("${Kannich.WORKSPACE_DIR}/.kannich_current_env")
        val hostEnv = mutableMapOf<String, String>()
        logger.debug("Reading current env from $currentEnvFile")
        withFileContentIfFileExists(currentEnvFile) { content ->
            logger.debug("Content: $content")
            // env entries are separated by \u0000
            content.split("\u0000").forEach { line ->
                logger.debug("Line: $line")
                val idx = line.indexOf('=')
                if (idx > 0) {
                    hostEnv[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
                }
            }
        }

        // delete the current env file for security reasons
        logger.debug("Deleting $currentEnvFile")
        FsUtil.delete(currentEnvFile).getOrElse { logger.warn("Failed to delete $currentEnvFile: ${it.message}") }
        logger.debug("Read env vars: ${hostEnv.keys.joinToString(", ")}")

        // filter variables, so we only keep the safe ones. look for a .kannichenv file in the
        // workspace. this has prefixes for allowed variables one by line. If that file does not exist use a default
        // prefix list. a line "<defaults>" in the .kannichenv file is a placeholder for the default list.
        val defaultPrefixes = listOf("CI_", "GITHUB_", "BUILD_", "CIRCLE_", "TRAVIS_", "BITBUCKET_", "KANNICH_")
        val effectivePrefixes = mutableSetOf<String>()
        val kannichEnvFile = Path.of("${Kannich.WORKSPACE_DIR}/.kannichenv")
        val usedKannichEnv = withFileContentIfFileExists(kannichEnvFile) { content ->
            content.lines().forEach { line ->
                // remove comments (anything following a #)
                when(val lineWithoutComments = line.substringBefore("#").trim()) {
                   "!defaults" -> effectivePrefixes.addAll(defaultPrefixes)
                   "" -> return@forEach
                   else -> effectivePrefixes.add(lineWithoutComments)
                }
            }
        }
        if (!usedKannichEnv) {
            effectivePrefixes.addAll(defaultPrefixes)
        }
        logger.debug("Filtering env vars for prefixes: $effectivePrefixes")

        // remove env vars that do not start with one of the prefixes
        return hostEnv.filterKeys { key -> effectivePrefixes.any { key.startsWith(it) } }
    }


    private fun withFileContentIfFileExists(path: Path, block: (String) -> Unit): Boolean {
        if (FsUtil.exists(path).getOrDefault(false)) {
            val content = FsUtil.readAsString(path).getOrElse {
                logger.error("Failed to read $path: ${it.message}")
                exitProcess(1)
            }
            block(content)
            return true
        }
        return false
    }

    private fun setupMavenRepository(settingsXml: String) {
        val m2Dir = File("/root/.m2")
        m2Dir.mkdirs()

        val target = if (devMode) {
            val devRepo = File(Kannich.DEV_REPO_DIR)
            if (!devRepo.exists()) {
                logger.error("Dev mode requires host .m2/repository to be mounted.")
                logger.error("Ensure ~/.m2/repository exists on host.")
                exitProcess(1)
            }
            logger.info("Dev mode: using host Maven repository")
            devRepo.toPath()
        } else {
            val cacheRepo = File("${Kannich.CACHE_DIR}/kannich-deps")
            cacheRepo.mkdirs()
            cacheRepo.toPath()
        }

        if (settingsXml.isNotBlank()) {
            val settingsFile = File(m2Dir, "settings.xml")
            settingsFile.writeText(settingsXml)
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

    private fun listPipeline(pipeline: Pipeline) {
        logger.info("Pipeline contents:")
        pipeline.executions.values.forEach { exec ->
            printEntity(exec)
            printSteps(exec.steps, "  ")
        }
    }

    private fun printSteps(steps: List<ExecutionStep>, indent: String, inParallel: Boolean = false) {
        steps.forEach { step ->
            val prefix = if (inParallel) "| - " else "- "
            when (step) {
                is JobExecutionStep -> {
                    printEntity(step.job, indent, prefix)
                }

                is ExecutionReference -> {
                    val exec = step.execution
                    printEntity(exec, indent, prefix)
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

    private fun printEntity(entity: Any, indent: String = "", prefix: String = "") {
        val (type, name, description) = when (entity) {
            is Execution -> Triple("Execution", entity.name, entity.description)
            is Job -> Triple("Job", entity.name, entity.description)
            else -> throw IllegalArgumentException("Unsupported entity type: ${entity::class.simpleName}")
        }
        val namePart = if (name != null) " $name" else ""
        val descPart = if (!description.isNullOrBlank()) " - $description" else ""
        logger.info("$indent$prefix$type$namePart$descPart")
    }
}

fun main(args: Array<String>) = KannichCommand().main(args)
