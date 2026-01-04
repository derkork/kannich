@file:DependsOn("dev.kannich:kannich-stdlib:0.1.0")
@file:DependsOn("dev.kannich:kannich-maven:0.1.0")
@file:DependsOn("dev.kannich:kannich-java:0.1.0")
@file:DependsOn("dev.kannich:kannich-trivy:0.1.0")
@file:DependsOn("dev.kannich:kannich-helm:0.1.0")


import dev.kannich.java.Java
import dev.kannich.maven.Maven
import dev.kannich.stdlib.*
import dev.kannich.stdlib.tools.*

pipeline {
    val java = Java("21")
    val maven = Maven("3.9.6", java)

    val deploy = job("deploy") {
        // fail fast, verify required variables
        val dockerUsername = getEnv("KANNICH_DOCKER_USERNAME") ?: fail("Please specify username for docker login in KANNICH_DOCKER_USERNAME")
        val dockerPassword = getEnv("KANNICH_DOCKER_PASSWORD") ?: fail("Please specify password/token for docker login in KANNICH_DOCKER_PASSWORD")
        val version = getEnv("KANNICH_VERSION") ?: fail("Please specify release version in KANNICH_VERSION.")
        val isLatest = getEnv("KANNICH_IS_LATEST") ?: "true"

        // check docker login.
        Docker.login(dockerUsername, dockerPassword)

        // set version to desired version
        maven.exec("-B", "versions:set", "-DnewVersion=$version")

        // build jar and image
        maven.exec("-B", "-Pbootstrap", "install")

        // push version to docker hub
        Docker.exec("push", "derkork/kannich:$version")

        if (isLatest == "true") {
            Docker.exec("tag", "derkork/kannich:$version", "derkork/kannich:latest")
            Docker.exec("push", "derkork/kannich:latest")
        }
    }


    execution("deploy") {
        job(deploy)
    }


    execution("test") {
        job("test") {
            secret("World!")
            logger.info("Getenv: ${getEnv("NOOT")}")
            Shell.execShell("echo 'Hello World!'")
            logger.info("Hello World!")
        }
    }
}
