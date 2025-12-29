package dev.kannich.docker

/**
 * Provides Docker-in-Docker support for Kannich pipelines.
 * Manages a Docker daemon inside the build container.
 */
class Docker(val version: String) {
    fun compose(block: ComposeConfig.() -> Unit) {
        val config = ComposeConfig().apply(block)
        // TODO: Execute docker-compose with the given configuration
        println("docker-compose -f ${config.file} ${config.command}")
    }
}

class ComposeConfig {
    var file: String = "docker-compose.yml"
    var command: String = "up"
}
