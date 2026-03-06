package dev.kannich.quarkus

import dev.kannich.maven.Maven
import dev.kannich.stdlib.Tool
import dev.kannich.tools.Git

/**
 * Provides Quarkus support for Kannich pipelines.
 * Uses Maven to run Quarkus commands.
 *
 * Usage:
 * ```kotlin
 * pipeline {
 *     val java = Java("21")
 *     val maven = Maven("3.9.6", java)
 *
 *     job {
 *         maven.quarkusNativeBuild()
 *     }
 * }
 * ```
 */

suspend fun Maven.quarkusNativeBuild(
    name: String = "output",
    tag: String = "latest",
    labels: Map<String, String> = emptyMap()
) {
    val additionalParams = mutableListOf<String>()

    fun MutableList<String>.addParam(key: String) {
        System.getProperty(key)?.let { value -> add("-D$key=$value") }
    }

    additionalParams.addParam("http.proxyHost")
    additionalParams.addParam("http.proxyPort")
    additionalParams.addParam("http.nonProxyHosts")
    additionalParams.addParam("http.proxyUser")
    additionalParams.addParam("http.proxyPassword")
    additionalParams.addParam("https.proxyHost")
    additionalParams.addParam("https.proxyPort")
    additionalParams.addParam("https.nonProxyHosts")
    additionalParams.addParam("https.proxyUser")
    additionalParams.addParam("https.proxyPassword")

    labels.forEach { (key, value) -> additionalParams.add("-Dquarkus.container-image.labels.$key=$value") }

    exec(
        "-B", "package",
        "-Dmaven.test.skip=true",
        "-Dquarkus.package.type=native",
        "-Dquarkus.native.container-build=true",
        "-Dquarkus.container-image.build=true",
        "-Dquarkus.container-image.builder=jib",
        "-Dquarkus.container-image.push=false",
        "-Dquarkus.jib.use-current-timestamp=true",
        "-Dquarkus.container-image.name=$name",
        "-Dquarkus.container-image.group=",
        "-Dquarkus.container-image.tag=$tag",
        *additionalParams.toTypedArray()
    )
}



