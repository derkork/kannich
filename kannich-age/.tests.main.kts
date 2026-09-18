@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-age:0.1.0")

import dev.kannich.age.Age
import dev.kannich.test.*
import dev.kannich.tools.Cache

testSuite {
    beforeAll {
        clearCaches("tools/age")
    }

    test("install and run age") {
        val age = Age("1.3.2")
        val result = age.exec("--version")
        verify(result.stdout.contains("1.3.2"), "Expected age version 1.3.2 in output")
    }

    test("age-keygen works") {
        val age = Age("1.3.2")
        age.keygen.exec("--help")
    }
}
