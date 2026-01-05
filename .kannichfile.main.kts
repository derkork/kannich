@file:DependsOn("dev.kannich:kannich-stdlib:0.1.0")
@file:DependsOn("dev.kannich:kannich-maven:0.1.0")
@file:DependsOn("dev.kannich:kannich-java:0.1.0")
@file:DependsOn("dev.kannich:kannich-trivy:0.1.0")


import dev.kannich.java.Java
import dev.kannich.maven.Maven
import dev.kannich.stdlib.*
import dev.kannich.stdlib.tools.*
import dev.kannich.trivy.Trivy

pipeline {
    val java = Java("21")
    val maven = Maven("3.9.6", java)
    val trivy = Trivy("0.68.2")


    val release = job("release") {
        // fail fast, verify required variables
        val dockerUsername = getEnv("KANNICH_DOCKER_USERNAME")
            ?: fail("Please specify username for docker login in KANNICH_DOCKER_USERNAME")
        val dockerPassword = getEnv("KANNICH_DOCKER_PASSWORD")
            ?: fail("Please specify password/token for docker login in KANNICH_DOCKER_PASSWORD")
        val version =
            getEnv("KANNICH_RELEASE_VERSION") ?: fail("Please specify release version in KANNICH_RELEASE_VERSION.")
        val setLatest = "true" == (getEnv("KANNICH_SET_LATEST") ?: "true")
        val dryRun = "true" == (getEnv("KANNICH_DRY_RUN") ?: "false")


        // set version to desired version
        log("Setting version to $version")
        maven.exec("-B", "-q", "versions:set", "-DnewVersion=$version")

        // build jar and docker image
        log("Building jar and docker image")
        maven.exec("-B", "-q", "-Pbootstrap", "install")

        // run trivy on docker image to detect vulnerabilities
        log("Checking for vulnerabilities in docker image")
        trivy.exec(
            "image",
            "derkork/kannich:$version",
            "--exit-code", "1",
            "--exit-on-eol", "1",
            "--severity", "CRITICAL",
            "--no-progress",
            "--format", "json",
            "--output", "trivy-results.json"
        )

        artifacts {
            includes("trivy-results.json")
        }


        // push version to docker hub
        if (!dryRun) {
            log("Publishing docker image to docker hub")
            Docker.login(dockerUsername, dockerPassword)
            Docker.exec("push", "derkork/kannich:$version")
            // also push to the "latest" tag if desired
            if (setLatest) {
                log("Setting latest tag")
                Docker.exec("tag", "derkork/kannich:$version", "derkork/kannich:latest")
                Docker.exec("push", "derkork/kannich:latest")
            }
        } else {
            log("Dry run: not pushing to docker hub.")
        }


    }


    execution("release") {
        job(release)
    }


    execution("test") {
        job("test") {
            secret("World!")
            log("Getenv: ${getEnv("NOOT")}")
            Shell.execShell("echo 'Hello World!'")
            log("Hello World!")
        }
    }
}
