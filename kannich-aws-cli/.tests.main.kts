@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-aws-cli:0.6.0")


import dev.kannich.awscli.AwsCli
import dev.kannich.test.*
import dev.kannich.tools.Cache

testSuite {
    beforeAll {
        clearCaches("tools/aws-cli")
    }

    test("install aws cli works") {
        val aws = AwsCli("2.17.44")
        aws.exec("--version")
    }
}
