package dev.kannich.maven

import dev.kannich.test.ContainerExecutor
import dev.kannich.test.KannichTestContainer
import dev.kannich.test.PipelineBuilder
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class InstallMavenIT : FunSpec({
    val container = install(KannichTestContainer.create())

    test("Maven module installs Maven 3.9.6 and runs mvn --version") {
        val executor = ContainerExecutor(container)

        val pipeline = PipelineBuilder()
            .withLocalModule()
            .import("dev.kannich.java.Java")
            .import("dev.kannich.maven.Maven")
            .job {
                """
                val java = Java("21")
                val maven = Maven("3.9.6", java)
                maven.exec("--version")
                """
            }

        val result = executor.run(pipeline)

        result.success shouldBe true
        result.stdout shouldContain "Apache Maven 3.9.6"
    }
})
