@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-trivy:0.10.0")


import dev.kannich.trivy.Trivy
import dev.kannich.test.*
import dev.kannich.tools.Cache

testSuite {
    beforeAll {
        clearCaches("tools/trivy")
    }

    test("install trivy works") {
        val trivy = Trivy("0.70.0")
        trivy.exec("version")
    }
}
