@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-node:0.5.0")


import dev.kannich.node.Node
import dev.kannich.test.*
import dev.kannich.tools.Cache

testSuite {
    beforeAll {
        clearCaches("tools/node")
    }

    test("install node works") {
        val node = Node("22.14.0")
        node.exec("--version")
    }

    test("npm works") {
        val node = Node("22.14.0")
        node.npm.exec("--version")
    }

    test("npx works") {
        val node = Node("22.14.0")
        node.npx.exec("--version")
    }
}
