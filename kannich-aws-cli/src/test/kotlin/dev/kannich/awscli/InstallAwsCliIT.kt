package dev.kannich.awscli

import dev.kannich.test.ContainerExecutor
import dev.kannich.test.KannichTestSpecExtension
import dev.kannich.test.PipelineBuilder
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class InstallAwsCliIT : FunSpec({
    val container = install(KannichTestSpecExtension())

    test("AWS CLI module installs AWS CLI and runs aws --version") {
        val executor = ContainerExecutor(container)

        val pipeline = PipelineBuilder()
            .withLocalModule()
            .import("dev.kannich.awscli.AwsCli")
            .job {
                """
                val aws = AwsCli("2.17.44")
                aws.exec("--version")
                """
            }

        val result = executor.run(pipeline)

        result.success shouldBe true
        result.stdout shouldContain "aws-cli/2.17.44"
    }
})
