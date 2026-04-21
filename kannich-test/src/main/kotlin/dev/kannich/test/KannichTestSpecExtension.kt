package dev.kannich.test

import com.github.dockerjava.api.model.ContainerNetwork
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
    private var internalNetwork: Network? = null

    /**
     * Path to the local Maven repository. Used to mount into the container
     * so kannich can find locally installed artifacts.
     */
    private val M2_REPOSITORY: String by lazy {
        System.getProperty("kannich.test.m2repo")
            ?: "${System.getProperty("user.home")}/.m2/repository"
    }

    override fun mount(configure: Unit.() -> Unit): GenericContainer<*> {
        val image = System.getProperty("kannich.test.image")
            ?: throw IllegalArgumentException("kannich.test.image system property must be set")
        logger.info("Creating kannich container with image: $image")
        logger.info("Mounting local Maven repository: $M2_REPOSITORY")

        // Kannich lives on this network only — no direct internet access, must go through Squid.
        internalNetwork = Network.builder()
            .createNetworkCmdModifier { it.withInternal(true) }
            .build()

        // Squid starts on the default bridge so it has internet access.
        squidContainer = GenericContainer(DockerImageName.parse("ubuntu/squid:latest"))
            .withExposedPorts(3128)
            .waitingFor(Wait.forListeningPort())
            .withLogConsumer(Slf4jLogConsumer(logger))

        squidContainer!!.start()

        // Connect Squid to the internal network so kannich can reach it.
        squidContainer!!.dockerClient
            .connectToNetworkCmd()
            .withContainerId(squidContainer!!.containerId)
            .withNetworkId(internalNetwork!!.id)
            .withContainerNetwork(ContainerNetwork().withAliases(listOf("squid-proxy")))
            .exec()

        kannichContainer = GenericContainer(image)
            .withPrivilegedMode(true)
            .withNetwork(internalNetwork)
            .withFileSystemBind(M2_REPOSITORY, "/kannich/cache/kannich-deps", BindMode.READ_WRITE)
            .withEnv("HTTP_PROXY", "http://squid-proxy:3128")
            .withEnv("HTTPS_PROXY", "http://squid-proxy:3128")
            .withEnv("http_proxy", "http://squid-proxy:3128")
            .withEnv("https_proxy", "http://squid-proxy:3128")
            .withEnv("NO_PROXY", "localhost,127.0.0.1")
            .withEnv("no_proxy", "localhost,127.0.0.1")
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
            internalNetwork?.close()
        }
    }
}
