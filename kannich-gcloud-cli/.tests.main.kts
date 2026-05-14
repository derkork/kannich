@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-gcloud-cli:0.5.0")


import dev.kannich.gcloud.GcloudCli
import dev.kannich.test.*
import dev.kannich.tools.Cache

testSuite {
    beforeAll {
        clearCaches("tools/gcloud-cli")
    }

    test("install gcloud cli works") {
        val gcloud = GcloudCli("490.0.0")
        gcloud.exec("--version")
    }
}
