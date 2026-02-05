package dev.kannich.tools

import dev.kannich.test.ContainerExecutor
import dev.kannich.test.KannichTestSpecExtension
import dev.kannich.test.PipelineBuilder
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class AptIT : FunSpec({
    val container = install(KannichTestSpecExtension())

    test("Apt.install installs tree package") {
        val executor = ContainerExecutor(container)

        val pipeline = PipelineBuilder.job {
            """Apt.install("tree")"""
        }

        println(pipeline.build())

        val result = executor.run(pipeline)
        result.success shouldBe true

        // Verify tree is installed by running it
        val verifyResult = executor.exec("tree", "--version")
        verifyResult.success shouldBe true
        verifyResult.stdout shouldContain "tree"
    }
})
