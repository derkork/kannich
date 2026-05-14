@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-terraform:0.5.0")


import dev.kannich.terraform.Terraform
import dev.kannich.test.*
import dev.kannich.tools.Cache

testSuite {
    beforeAll {
        clearCaches("tools/terraform")
    }

    test("install terraform works") {
        val terraform = Terraform("1.11.0")
        terraform.exec("version")
    }
}
