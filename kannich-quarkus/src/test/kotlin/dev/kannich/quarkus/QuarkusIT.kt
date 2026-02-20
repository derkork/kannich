package dev.kannich.quarkus

import dev.kannich.test.ContainerExecutor
import dev.kannich.test.KannichTestSpecExtension
import dev.kannich.test.PipelineBuilder
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class QuarkusIT : FunSpec({
    val container = install(KannichTestSpecExtension())

    test("Quarkus ensures Maven is installed") {
        val executor = ContainerExecutor(container)

        val pipeline = PipelineBuilder()
            .withLocalModule()
            .import("dev.kannich.java.Java")
            .import("dev.kannich.maven.Maven")
            .import("dev.kannich.quarkus.Quarkus")
            .job {
                """
                val java = Java("21")
                val maven = Maven("3.9.6", java)
                val quarkus = Quarkus(maven)
                quarkus.ensureInstalled()               
                """
            }

        val result = executor.run(pipeline)

        result.success shouldBe true
    }
})
