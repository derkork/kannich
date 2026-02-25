package dev.kannich.precommit

import dev.kannich.test.ContainerExecutor
import dev.kannich.test.KannichTestSpecExtension
import dev.kannich.test.PipelineBuilder
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class PreCommitIT : FunSpec({
    val container = install(KannichTestSpecExtension())

    test("PreCommit module installs pre-commit and runs --version") {
        val executor = ContainerExecutor(container)

        val pipeline = PipelineBuilder()
            .withLocalModule()
            .import("dev.kannich.precommit.PreCommit")
            .job {
                """
                val preCommit = PreCommit("4.0.1")
                preCommit.exec("--version")
                """
            }

        val result = executor.run(pipeline)

        result.success shouldBe true
        result.stdout shouldContain "pre-commit 4.0.1"
    }

    // TODO: fix the problem that kannich cannot run twice in the same container, then re-enable this test.
    test("PreCommit module can run sample-config command") {
        val executor = ContainerExecutor(container)

        val pipeline = PipelineBuilder()
            .withLocalModule()
            .import("dev.kannich.precommit.PreCommit")
            .job {
                """
                val preCommit = PreCommit("4.0.1")
                preCommit.exec("sample-config")
                """
            }

        val result = executor.run(pipeline)

        result.success shouldBe true
        result.stdout shouldContain "repos:"
        result.stdout shouldContain "hooks:"
    }
})
