package dev.kannich.uv

import dev.kannich.test.ContainerExecutor
import dev.kannich.test.KannichTestSpecExtension
import dev.kannich.test.PipelineBuilder
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class InstallUvIT : FunSpec({
    val container = install(KannichTestSpecExtension())

    test("UV module installs UV and runs uv --version") {
        val executor = ContainerExecutor(container)

        val pipeline = PipelineBuilder()
            .withLocalModule()
            .import("dev.kannich.uv.Uv")
            .job {
                """
                val uv = Uv("0.10.7")
                uv.exec("--version")
                """
            }

        val result = executor.run(pipeline)

        result.success shouldBe true
        result.stdout shouldContain "uv 0.10.7"
    }
})
