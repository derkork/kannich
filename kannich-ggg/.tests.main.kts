@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-ggg:0.1.0")


import dev.kannich.ggg.Ggg
import dev.kannich.test.*

testSuite {
    beforeAll {
        clearCaches("tools/ggg")
    }

    test("install ggg works") {
        val ggg = Ggg("0.4.0")
        ggg.exec("--version")
    }
}
