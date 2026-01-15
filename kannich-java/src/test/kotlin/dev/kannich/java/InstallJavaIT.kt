package dev.kannich.java

import dev.kannich.test.ContainerExecutor
import dev.kannich.test.KannichTestContainer
import dev.kannich.test.PipelineBuilder
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class InstallJavaIT : FunSpec({
    val container = install(KannichTestContainer.create())

    test("Java module installs Java 21 and runs java --version") {
        val executor = ContainerExecutor(container)

        val pipeline = PipelineBuilder()
            .withLocalModule()
            .import("dev.kannich.java.Java")
            .job {
                """
                val java = Java("21")
                java.exec("--version")
                """
            }

        val result = executor.run(pipeline)

        result.success shouldBe true
        result.stdout shouldContain "openjdk 21"
    }
})
