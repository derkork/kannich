@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-ggg:0.1.0")


import dev.kannich.ggg.Ggg
import dev.kannich.stdlib.Arch
import dev.kannich.test.*

testSuite {
    beforeAll {
        clearCaches("tools/ggg")
    }

    test("install ggg works") {
        if (Arch.current == Arch.Arm64) {
            logWarning("Skipping ggg test on ARM64, as it is not supported yet")
            return@test
        }
        val ggg = Ggg("0.4.0")
        ggg.exec("--version")
    }
}
