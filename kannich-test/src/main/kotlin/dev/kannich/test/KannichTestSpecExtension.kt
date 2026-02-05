package dev.kannich.test

import com.github.dockerjava.api.exception.NotFoundException
import io.kotest.core.extensions.MountableExtension
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.spec.Spec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

class KannichTestSpecExtension : MountableExtension<Unit, GenericContainer<*>>, AfterSpecListener {

    private val logger = LoggerFactory.getLogger(KannichTestSpecExtension::class.java)
    private var kannichContainer: GenericContainer<*>? = null
    private var squidContainer: GenericContainer<*>? = null
    private var network: Network? = null

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
    private val DOWNLOAD_CACHE_VOLUME = "kannich-download-cache"

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
    override fun mount(configure: Unit.() -> Unit): GenericContainer<*> {
        val image = System.getProperty("kannich.test.image") ?: throw IllegalArgumentException("kannich.test.image system property must be set")
        logger.info("Creating kannich container with image: $image")
        logger.info("Mounting local Maven repository: $M2_REPOSITORY")
        ensureDownloadCacheVolume()

        val network = Network.newNetwork()

        squidContainer = GenericContainer(DockerImageName.parse("ubuntu/squid:latest"))
            .withNetwork(network)
            .withNetworkAliases("squid-proxy")
            .withCreateContainerCmdModifier { cmd ->
                val volumeBind = com.github.dockerjava.api.model.Bind(
                    DOWNLOAD_CACHE_VOLUME,
                    com.github.dockerjava.api.model.Volume("/var/spool/squid")
                )
                cmd.hostConfig?.withBinds(volumeBind)
            }
            .withLogConsumer(Slf4jLogConsumer(logger))

        squidContainer!!.start()

        kannichContainer = GenericContainer(image)
            .withPrivilegedMode(true)
            .withNetwork(network)
            .withFileSystemBind(M2_REPOSITORY, "/kannich/cache/kannich-deps", BindMode.READ_WRITE)
            .withCreateContainerCmdModifier { cmd ->
                cmd.withEntrypoint("bash", "-c")
                cmd.withCmd("exec sleep infinity")
            }
            .waitingFor(Wait.forSuccessfulCommand("echo ready"))
            .withLogConsumer(Slf4jLogConsumer(logger))

        kannichContainer!!.start()
        return kannichContainer!!
    }

    override suspend fun afterSpec(spec: Spec) {
        runInterruptible(Dispatchers.IO) {
            kannichContainer?.stop()
            squidContainer?.stop()
            network?.close()
        }
    }
}
