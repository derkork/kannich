@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-sops:0.1.0")

import dev.kannich.sops.Sops
import dev.kannich.test.*
import dev.kannich.tools.Cache

testSuite {
    beforeAll {
        clearCaches("tools/sops")
    }

    test("install and run sops") {
        val sops = Sops("3.13.3")
        val result = sops.exec("--version")
        verify(result.stdout.contains("3.13.3"), "Expected sops version 3.13.3 in output")
    }
}
