package dev.kannich.test

import io.kotest.extensions.testcontainers.ContainerExtension
import io.kotest.extensions.testcontainers.ContainerLifecycleMode
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import java.nio.file.Files
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
     * Creates a kannich container extension for use with Kotest.
     * Automatically mounts the local Maven repository for access to locally installed artifacts.
     */
    fun create(
        image: String = DEFAULT_IMAGE,
        lifecycleMode: ContainerLifecycleMode = ContainerLifecycleMode.Spec
    ): ContainerExtension<GenericContainer<*>> {
        logger.info("Creating kannich container with image: $image")
        logger.info("Mounting local Maven repository: $M2_REPOSITORY")
        val container = GenericContainer(image)
            .withPrivilegedMode(true)
            .withFileSystemBind(M2_REPOSITORY, "/kannich/cache/kannich-deps", BindMode.READ_ONLY)
            .withCreateContainerCmdModifier { cmd ->
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
     */
    fun createWithMount(
        hostPath: Path,
        image: String = DEFAULT_IMAGE,
        lifecycleMode: ContainerLifecycleMode = ContainerLifecycleMode.Spec
    ): ContainerExtension<GenericContainer<*>> {
        logger.info("Creating kannich container with image: $image, mount: $hostPath")
        logger.info("Mounting local Maven repository: $M2_REPOSITORY")
        val container = GenericContainer(image)
            .withPrivilegedMode(true)
            .withFileSystemBind(M2_REPOSITORY, "/root/.m2/repository", BindMode.READ_ONLY)
            .withFileSystemBind(
                hostPath.toAbsolutePath().toString(),
                "/workspace",
                BindMode.READ_WRITE
            )
            .withCreateContainerCmdModifier { cmd ->
                cmd.withEntrypoint("bash", "-c")
                cmd.withCmd("exec sleep infinity")
            }
            .waitingFor(Wait.forSuccessfulCommand("echo ready"))
            .withLogConsumer(Slf4jLogConsumer(logger))
        return ContainerExtension(container, lifecycleMode)
    }

    /**
     * Creates a kannich container with a temporary directory mounted.
     * Automatically mounts the local Maven repository for access to locally installed artifacts.
     */
    fun createWithTempMount(
        prefix: String = "kannich-test",
        image: String = DEFAULT_IMAGE,
        lifecycleMode: ContainerLifecycleMode = ContainerLifecycleMode.Spec,
        setup: (Path) -> Unit = {}
    ): Pair<ContainerExtension<GenericContainer<*>>, Path> {
        val tempDir = Files.createTempDirectory(prefix)
        setup(tempDir)
        return createWithMount(tempDir, image, lifecycleMode) to tempDir
    }
}
