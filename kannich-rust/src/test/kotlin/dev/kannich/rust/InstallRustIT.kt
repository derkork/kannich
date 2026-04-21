package dev.kannich.rust

import dev.kannich.test.ContainerExecutor
import dev.kannich.test.KannichTestSpecExtension
import dev.kannich.test.PipelineBuilder
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class InstallRustIT : FunSpec({
    val container = install(KannichTestSpecExtension())

    test("Cargo module installs stable toolchain and runs cargo --version") {
        val executor = ContainerExecutor(container)

        val pipeline = PipelineBuilder()
            .withLocalModule()
            .dependsOn("dev.kannich:kannich-zig:0.1.0")
            .import("dev.kannich.rust.Cargo")
            .import("dev.kannich.zig.Zig")
            .job {
                """
                val cargo = Cargo(Zig("0.13.0"))
                cargo.exec("--version")
                """
            }

        val result = executor.run(pipeline)

        result.success shouldBe true
        result.stdout shouldContain "cargo"
    }

    test("Cargo module installs specific toolchain version and runs cargo --version") {
        val executor = ContainerExecutor(container)

        val pipeline = PipelineBuilder()
            .withLocalModule()
            .dependsOn("dev.kannich:kannich-zig:0.1.0")
            .import("dev.kannich.rust.Cargo")
            .import("dev.kannich.zig.Zig")
            .job {
                """
                val cargo = Cargo(Zig("0.13.0"), "1.85.0")
                cargo.exec("--version")
                """
            }

        val result = executor.run(pipeline)

        result.success shouldBe true
        result.stdout shouldContain "cargo 1.85.0"
    }
})
