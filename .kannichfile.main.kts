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
    val trivy = Trivy("0.68.2")


    fun requireEnv(name: String): String {
        return getEnv(name) ?: fail("Please specify $name in environment variables.")
    }

    fun envToggle(name: String, defaultValue: Boolean = false): Boolean {
        return "true" == (getEnv(name) ?: "$defaultValue")
    }


    val release = job("release", "Releases Kannich to Docker Hub and Maven Central") {
        val dockerUsername = requireEnv("KANNICH_DOCKER_USERNAME")
        val dockerPassword = secret(requireEnv("KANNICH_DOCKER_PASSWORD"))
        val gpgKey = requireEnv("KANNICH_GPG_KEY")
        val gpgPassphrase = secret(requireEnv("KANNICH_GPG_PASSPHRASE"))
        val version = requireEnv("KANNICH_VERSION")
        val sonatypeUsername = requireEnv("KANNICH_SONATYPE_USERNAME")
        val sonatypePassword = secret(requireEnv("KANNICH_SONATYPE_PASSWORD"))

        val setLatest = envToggle("KANNICH_SET_LATEST", true)
        val dryRun = envToggle("KANNICH_DRY_RUN")

        val maven = Maven("3.9.6", java) {
            server("ossrh") {
                username = sonatypeUsername
                password = sonatypePassword
            }
        }

        Gpg.importKey(gpgKey)

        // set version to desired version
        log("Setting version to $version")
        maven.exec("-B", "-q", "versions:set", "-DnewVersion=$version")

        // build cli jar and docker image
        log("Building jar and docker image")
        maven.exec("-B", "-q", "-Pbootstrap", "install", "-DskipTests")

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

            log("Publishing to Maven Central")
            withEnv(mapOf("MAVEN_GPG_PASSPHRASE" to gpgPassphrase)) {
                maven.exec("-B", "-Prelease", "deploy", "-DskipTests")
            }
        } else {
            log("Dry run: not pushing.")
        }
    }


    execution("release", "Releases Kannich to Docker Hub and Maven Central") {
        job(release)
    }
}
