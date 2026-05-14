@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-uv:0.6.0")


import dev.kannich.uv.Uv
import dev.kannich.test.*
import dev.kannich.tools.Cache

testSuite {
    beforeAll {
        clearCaches("uv")
    }

    test("install uv works") {
        val uv = Uv("0.6.14")
        uv.exec("--version")
    }
}
