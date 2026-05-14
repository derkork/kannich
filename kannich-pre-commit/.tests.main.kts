@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-pre-commit:0.6.0")


import dev.kannich.precommit.PreCommit
import dev.kannich.test.*
import dev.kannich.tools.Cache

testSuite {
    beforeAll {
        clearCaches("tools/pre-commit")
    }

    test("install pre-commit works") {
        val preCommit = PreCommit("4.0.1")
        preCommit.exec("--version")
    }

    test("pre-commit sample-config works") {
        val preCommit = PreCommit("4.0.1")
        preCommit.exec("sample-config")
    }
}
