package dev.kannich.maven

import dev.kannich.stdlib.Kannich
import dev.kannich.test.ContainerExecutor
import dev.kannich.test.KannichTestSpecExtension
import dev.kannich.test.PipelineBuilder
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class MavenConfigurationIT : FunSpec({
    val container = install(KannichTestSpecExtension())

    test("Maven module properly creates settings.xml with proxy settings.") {
        val executor = ContainerExecutor(container)

        val pipeline = PipelineBuilder()
            .withLocalModule()
            .import("dev.kannich.java.Java")
            .import("dev.kannich.maven.Maven")
            .job {
                // language="kotlin"
                """
                val java = Java("21")
                val maven = Maven("3.9.6", java)
                maven.exec("--version")
                
                // verify that settings.xml was created with proxy settings.
                val settingsXml = Fs.readAsString(".kannich/settings.xml")
                log(settingsXml)
                if (!settingsXml.contains("http_proxy")) {
                    fail("settings.xml does not contain http_proxy")
                }
                """
            }

        val result = executor.run(pipeline)

        result.success shouldBe true
        result.stdout shouldContain "Apache Maven 3.9.6"
    }
})
