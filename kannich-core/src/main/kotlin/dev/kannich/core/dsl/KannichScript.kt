package dev.kannich.core.dsl

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.CompoundDependenciesResolver
import kotlin.script.experimental.dependencies.DependsOn
import kotlin.script.experimental.dependencies.FileSystemDependenciesResolver
import kotlin.script.experimental.dependencies.Repository
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver
import kotlin.script.experimental.dependencies.resolveFromScriptSourceAnnotations
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Defines .kannichfile.main.kts as a Kotlin script with Maven dependency support.
 * Users add @file:DependsOn("group:artifact:version") to load libraries.
 * The .main.kts suffix enables IDE autocomplete in IntelliJ IDEA.
 *
 * For IDE script definition discovery, this class must be registered in:
 * META-INF/kotlin/script/templates/dev.kannich.core.dsl.KannichScript
 * That file must contain the fully qualified class name on a single line.
 */
@KotlinScript(
    displayName = "Kannich Build Script",
    fileExtension = "kannichfile.main.kts",
    compilationConfiguration = KannichScriptCompilationConfiguration::class,
    evaluationConfiguration = KannichScriptEvaluationConfiguration::class
)
abstract class KannichScript

object KannichScriptCompilationConfiguration : ScriptCompilationConfiguration({
    // Enable @file:DependsOn and @file:Repository annotations
    defaultImports(DependsOn::class, Repository::class)

    // Provide 'kannich' as a script property for accessing build context
    providedProperties("kannich" to KannichContext::class)

    // Include kannich-core on the classpath (stdlib resolved via @DependsOn)
    jvm {
        dependenciesFromCurrentContext(wholeClasspath = true)
    }

    // Configure Maven dependency resolution for @file:DependsOn
    refineConfiguration {
        onAnnotations(DependsOn::class, Repository::class, handler = ::configureMavenDepsOnAnnotations)
    }
})

object KannichScriptEvaluationConfiguration : ScriptEvaluationConfiguration({
    // The 'kannich' property is provided at evaluation time via providedProperties
})

private val logger = LoggerFactory.getLogger(KannichScript::class.java)

// Compound resolver that checks filesystem first, then Maven
private val resolver = CompoundDependenciesResolver(
    FileSystemDependenciesResolver(),
    MavenDependenciesResolver()
)

/**
 * Resolves Maven dependencies specified via @file:DependsOn annotations.
 * Uses the built-in resolveFromScriptSourceAnnotations method.
 */
fun configureMavenDepsOnAnnotations(
    context: ScriptConfigurationRefinementContext
): ResultWithDiagnostics<ScriptCompilationConfiguration> {
    val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)
        ?.takeIf { it.isNotEmpty() }
        ?: return context.compilationConfiguration.asSuccess()

    logger.debug("Found ${annotations.size} annotations to resolve")

    return runBlocking {
        resolver.resolveFromScriptSourceAnnotations(annotations)
    }.onSuccess { resolvedFiles ->
        logger.debug("Resolved ${resolvedFiles.size} files: ${resolvedFiles.map { it.name }}")
        context.compilationConfiguration.with {
            dependencies.append(JvmDependency(resolvedFiles))
        }.asSuccess()
    }
}
