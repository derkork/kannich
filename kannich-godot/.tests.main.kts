@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-godot:0.1.0")


import dev.kannich.godot.Ggg
import dev.kannich.test.*

testSuite {
    beforeAll {
        clearCaches("tools/ggg")
    }

    test("install ggg works") {
        val ggg = Ggg("0.3.1")
        ggg.exec("--version")
    }
}
