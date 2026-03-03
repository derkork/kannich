package dev.kannich.gcloud

import dev.kannich.test.ContainerExecutor
import dev.kannich.test.KannichTestSpecExtension
import dev.kannich.test.PipelineBuilder
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class InstallGcloudCliIT : FunSpec({
    val container = install(KannichTestSpecExtension())

    test("Google Cloud CLI module installs gcloud CLI and runs gcloud --version") {
        val executor = ContainerExecutor(container)

        val pipeline = PipelineBuilder()
            .withLocalModule()
            .import("dev.kannich.gcloud.GcloudCli")
            .job {
                """
                val gcloud = GcloudCli("558.0.0")
                gcloud.exec("--version")
                """
            }

        val result = executor.run(pipeline)

        result.success shouldBe true
        result.stdout shouldContain "Google Cloud SDK 558.0.0"
    }
})
