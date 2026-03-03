package dev.kannich.terraform

import dev.kannich.test.ContainerExecutor
import dev.kannich.test.KannichTestSpecExtension
import dev.kannich.test.PipelineBuilder
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class InstallTerraformIT : FunSpec({
    val container = install(KannichTestSpecExtension())

    test("Terraform module installs Terraform and runs terraform -version") {
        val executor = ContainerExecutor(container)

        val pipeline = PipelineBuilder()
            .withLocalModule()
            .import("dev.kannich.terraform.Terraform")
            .job {
                """
                val terraform = Terraform("1.14.6")
                terraform.exec("-version")
                """
            }

        val result = executor.run(pipeline)

        result.success shouldBe true
        result.stdout shouldContain "Terraform v1.14.6"
    }
})
