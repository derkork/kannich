package dev.kannich.node

import dev.kannich.test.ContainerExecutor
import dev.kannich.test.KannichTestSpecExtension
import dev.kannich.test.PipelineBuilder
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class InstallNodeIT : FunSpec({
    val container = install(KannichTestSpecExtension())

    test("Node.js module installs Node and runs node --version") {
        val executor = ContainerExecutor(container)

        val pipeline = PipelineBuilder()
            .withLocalModule()
            .import("dev.kannich.node.Node")
            .job {
                """
                val node = Node("24.14.0")
                node.exec("--version")
                node.npm.exec("--version")
                node.npx.exec("--version")
                """
            }

        val result = executor.run(pipeline)

        result.success shouldBe true
        result.stdout shouldContain "v24.14.0"
    }
})
