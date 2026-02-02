package dev.kannich.test

import com.github.dockerjava.api.exception.NotFoundException
import io.kotest.extensions.testcontainers.ContainerExtension
import io.kotest.extensions.testcontainers.ContainerLifecycleMode
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.nio.file.Path

object KannichTestContainer {

    private val logger = LoggerFactory.getLogger(KannichTestContainer::class.java)

    val DEFAULT_IMAGE: String = System.getProperty(
        "kannich.test.image",
        "derkork/kannich:0.3.0"
    )

    /**
     * Path to the local Maven repository. Used to mount into the container
     * so kannich can find locally installed artifacts.
     */
    private val M2_REPOSITORY: String by lazy {
        System.getProperty("kannich.test.m2repo")
            ?: "${System.getProperty("user.home")}/.m2/repository"
    }

    /**
     * Name of the Docker volume for the download cache.
     */
    private const val DOWNLOAD_CACHE_VOLUME = "kannich-download-cache"

    /**
     * Ensures the download cache volume exists, creating it if necessary.
     */
    private fun ensureDownloadCacheVolume() {
        val dockerClient = GenericContainer(DockerImageName.parse("alpine:latest"))
            .also { it.start() }
            .dockerClient

        try {
            dockerClient.inspectVolumeCmd(DOWNLOAD_CACHE_VOLUME).exec()
            logger.info("Using existing download cache volume: $DOWNLOAD_CACHE_VOLUME")
        } catch (e: NotFoundException) {
            dockerClient.createVolumeCmd()
                .withName(DOWNLOAD_CACHE_VOLUME)
                .exec()
            logger.info("Created new download cache volume: $DOWNLOAD_CACHE_VOLUME")
        }
    }

    /**
     * Creates a kannich container extension for use with Kotest.
     * Automatically mounts the local Maven repository for access to locally installed artifacts.
     * Mounts a persistent download cache volume to speed up repeated downloads across test runs.
     */
    fun create(
        image: String = DEFAULT_IMAGE,
        lifecycleMode: ContainerLifecycleMode = ContainerLifecycleMode.Spec
    ): ContainerExtension<GenericContainer<*>> {
        logger.info("Creating kannich container with image: $image")
        logger.info("Mounting local Maven repository: $M2_REPOSITORY")
        ensureDownloadCacheVolume()
        val container = GenericContainer(image)
            .withPrivilegedMode(true)
            .withFileSystemBind(M2_REPOSITORY, "/kannich/cache/kannich-deps", BindMode.READ_WRITE)
            .withCreateContainerCmdModifier { cmd ->
                cmd.hostConfig?.withBinds(
                    com.github.dockerjava.api.model.Bind(
                        DOWNLOAD_CACHE_VOLUME,
                        com.github.dockerjava.api.model.Volume("/kannich/cache/downloads")
                    )
                )
                cmd.withEntrypoint("bash", "-c")
                cmd.withCmd("exec sleep infinity")
            }
            .waitingFor(Wait.forSuccessfulCommand("echo ready"))
            .withLogConsumer(Slf4jLogConsumer(logger))
        return ContainerExtension(container, lifecycleMode)
    }

    /**
     * Creates a kannich container with a host directory mounted to /workspace.
     * Automatically mounts the local Maven repository for access to locally installed artifacts.
     * Mounts a persistent download cache volume to speed up repeated downloads across test runs.
     */
    fun createWithMount(
        hostPath: Path,
        image: String = DEFAULT_IMAGE,
        lifecycleMode: ContainerLifecycleMode = ContainerLifecycleMode.Spec
    ): ContainerExtension<GenericContainer<*>> {
        logger.info("Creating kannich container with image: $image, mount: $hostPath")
        logger.info("Mounting local Maven repository: $M2_REPOSITORY")
        ensureDownloadCacheVolume()
        val container = GenericContainer(image)
            .withPrivilegedMode(true)
            .withFileSystemBind(M2_REPOSITORY, "/root/.m2/repository", BindMode.READ_ONLY)
            .withFileSystemBind(
                hostPath.toAbsolutePath().toString(),
                "/workspace",
                BindMode.READ_WRITE
            )
            .withCreateContainerCmdModifier { cmd ->
                cmd.hostConfig?.withBinds(
                    com.github.dockerjava.api.model.Bind(
                        DOWNLOAD_CACHE_VOLUME,
                        com.github.dockerjava.api.model.Volume("/kannich/cache/downloads")
                    )
                )
                cmd.withEntrypoint("bash", "-c")
                cmd.withCmd("exec sleep infinity")
            }
            .waitingFor(Wait.forSuccessfulCommand("echo ready"))
            .withLogConsumer(Slf4jLogConsumer(logger))
        return ContainerExtension(container, lifecycleMode)
    }

}
