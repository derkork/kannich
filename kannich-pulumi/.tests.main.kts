@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-pulumi:0.1.0")


import dev.kannich.pulumi.Pulumi
import dev.kannich.test.*
import dev.kannich.tools.Cache

testSuite {
    beforeAll {
        clearCaches("tools/pulumi")
    }

    test("install pulumi works") {
        val pulumi = Pulumi("3.252.0")
        pulumi.exec("version")
    }

    test("pulumi downloads and caches a provider") {
        val pulumi = Pulumi("3.252.0")
        pulumi.exec("plugin", "install", "resource", "random", "v4.21.0")
        val result = pulumi.exec("plugin", "ls")
        verify(result.stdout.contains("random"), "Expected random provider in plugin list")
    }
}
