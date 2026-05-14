@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-helm:0.10.0")

import dev.kannich.helm.Helm
import dev.kannich.test.*
import dev.kannich.tools.Cache

testSuite {
    beforeAll {
       clearCaches("tools/helm")
    }

    test("install and run helm") {
        val helm = Helm("3.17.3")
        val result = helm.exec("version", "--short")
        verify(result.stdout.contains("v3.17.3"), "Expected helm version v3.17.3 in output")
    }
}
