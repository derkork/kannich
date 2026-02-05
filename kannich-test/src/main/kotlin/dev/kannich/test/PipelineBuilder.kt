package dev.kannich.test

/**
 * DSL for building kannich pipeline files for integration tests.
 *
 * By default includes kannich-stdlib and kannich-tools dependencies
 * with standard imports.
 *
 * ## Testing Your Module
 *
 * Configure Failsafe in your pom.xml to pass module coordinates:
 * ```xml
 * <plugin>
 *     <groupId>org.apache.maven.plugins</groupId>
 *     <artifactId>maven-failsafe-plugin</artifactId>
 *     <configuration>
 *         <systemPropertyVariables>
 *             <kannich.core.version>...</kannich.core.version>
 *             <kannich.test.m2repo>${settings.localRepository}</kannich.test.m2repo>
 *             <kannich.test.module.groupId>${project.groupId}</kannich.test.module.groupId>
 *             <kannich.test.module.artifactId>${project.artifactId}</kannich.test.module.artifactId>
 *             <kannich.test.module.version>${project.version}</kannich.test.module.version>
 *         </systemPropertyVariables>
 *     </configuration>
 * </plugin>
 * ```
 *
 * Then in tests:
 * ```kotlin
 * // Simple test using only stdlib/tools
 * val pipeline = PipelineBuilder.job {
 *     """Apt.install("tree")"""
 * }
 *
 * // Test with your module (coordinates from pom.xml)
 * val pipeline = PipelineBuilder()
 *     .withLocalModule()
 *     .import("com.example.MyTool")
 *     .job { "MyTool.doSomething()" }
 * ```
 */
class PipelineBuilder {
    private val extraDependencies = mutableListOf<String>()
    private val extraImports = mutableListOf<String>()
    private var pipelineBody: String = ""
    private var executionName: String = "test"

    /**
     * Adds the local module as a dependency.
     * Coordinates are taken from system properties set by Maven Failsafe:
     * - kannich.test.module.groupId
     * - kannich.test.module.artifactId
     * - kannich.test.module.version
     *
     * This works for both kannich core modules and third-party modules.
     */
    fun withLocalModule() = apply {
        val groupId = MODULE_GROUP_ID
            ?: throw IllegalStateException("kannich.test.module.groupId not set")
        val artifactId = MODULE_ARTIFACT_ID
            ?: throw IllegalStateException("kannich.test.module.artifactId not set")
        val version = MODULE_VERSION
            ?: throw IllegalStateException("kannich.test.module.version not set")
        extraDependencies.add("$groupId:$artifactId:$version")
    }

    /**
     * Adds additional dependencies with explicit coordinates.
     */
    fun dependsOn(vararg coords: String) = apply {
        extraDependencies.addAll(coords)
    }

    /**
     * Adds additional imports beyond the default stdlib and tools imports.
     */
    fun import(vararg packages: String) = apply {
        extraImports.addAll(packages)
    }

    /**
     * Sets the execution name (default: "test").
     */
    fun executionName(name: String) = apply {
        executionName = name
    }

    /**
     * Sets the full pipeline body. Use this when you need full control
     * over the pipeline structure.
     */
    fun pipeline(body: () -> String) = apply {
        pipelineBody = body()
    }

    /**
     * Creates a simple pipeline with a single execution and job.
     * The lambda should return the job body code.
     */
    fun job(body: () -> String) = apply {
        pipelineBody = """
            pipeline {
                execution("$executionName") {
                    job {
                        ${body().prependIndent("                        ").trim()}
                    }
                }
            }
        """.trimIndent()
    }

    fun build(): String = buildString {
        if (VERSION == null) {
            throw IllegalStateException("kannich.core.version system property not set")
        }

        // Default dependencies
        appendLine("@file:DependsOn(\"dev.kannich:kannich-stdlib:$VERSION\")")
        appendLine("@file:DependsOn(\"dev.kannich:kannich-tools:$VERSION\")")

        // Extra dependencies
        extraDependencies.forEach { dep ->
            appendLine("@file:DependsOn(\"$dep\")")
        }

        appendLine()

        // Default imports
        appendLine("import dev.kannich.stdlib.*")
        appendLine("import dev.kannich.tools.*")

        // Extra imports
        extraImports.forEach { imp ->
            appendLine("import $imp")
        }

        appendLine()
        append(pipelineBody)
    }

    companion object {
        /**
         * Kannich version from kannich.core.version system property.
         * Used for default kannich-stdlib and kannich-tools dependencies.
         */
        val VERSION: String? = System.getProperty("kannich.core.version")

        /** Local module groupId from pom.xml via Failsafe */
        val MODULE_GROUP_ID: String? = System.getProperty("kannich.test.module.groupId")

        /** Local module artifactId from pom.xml via Failsafe */
        val MODULE_ARTIFACT_ID: String? = System.getProperty("kannich.test.module.artifactId")

        /** Local module version from pom.xml via Failsafe */
        val MODULE_VERSION: String? = System.getProperty("kannich.test.module.version")

        /**
         * Creates a simple pipeline with a single job.
         * This is the most common pattern for integration tests.
         */
        fun job(executionName: String = "test", body: () -> String): PipelineBuilder {
            return PipelineBuilder()
                .executionName(executionName)
                .job(body)
        }
    }
}
