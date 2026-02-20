@file:DependsOn("dev.kannich:kannich-stdlib:0.5.0")
@file:DependsOn("dev.kannich:kannich-tools:0.5.0")
@file:DependsOn("dev.kannich:kannich-maven:0.5.0")
@file:DependsOn("dev.kannich:kannich-java:0.5.0")
@file:DependsOn("dev.kannich:kannich-trivy:0.5.0")
@file:DependsOn("dev.kannich:kannich-helm:0.5.0")


import dev.kannich.java.Java
import dev.kannich.maven.Maven
import dev.kannich.stdlib.*
import dev.kannich.tools.*
import dev.kannich.trivy.Trivy
import dev.kannich.helm.Helm

pipeline {
    val java = Java("21")
    val trivy = Trivy("0.68.2")

    suspend fun envToggle(name: String, defaultValue: Boolean = false): Boolean {
        return "true" == (getEnv(name) ?: "$defaultValue")
    }

    execution("release-module", "Description releases a single module") {
        job {
            val gpgKey = requireEnv("KANNICH_GPG_KEY")
            val gpgPassphrase = secret(requireEnv("KANNICH_GPG_PASSPHRASE"))
            val sonatypeUsername = requireEnv("KANNICH_SONATYPE_USERNAME")
            val sonatypePassword = secret(requireEnv("KANNICH_SONATYPE_PASSWORD"))
            val moduleName = requireEnv("KANNICH_MODULE")

            Gpg.importKey(gpgKey)

            val maven = Maven("3.9.6", java) {
                server("ossrh") {
                    username = sonatypeUsername
                    password = sonatypePassword
                }
            }

            maven.exec("-B", "-q", "install", "-DskipTests")

            log("Publishing to Maven Central")
            withEnv(mapOf("MAVEN_GPG_PASSPHRASE" to gpgPassphrase)) {
                maven.exec("-B", "-Prelease", "deploy", "-DskipTests", "-pl", moduleName)
            }
        }
    }

    execution("release", "Releases Kannich to Docker Hub and Maven Central") {
        job {
            val dockerUsername = requireEnv("KANNICH_DOCKER_USERNAME")
            val dockerPassword = secret(requireEnv("KANNICH_DOCKER_PASSWORD"))
            val gpgKey = requireEnv("KANNICH_GPG_KEY")
            val gpgPassphrase = secret(requireEnv("KANNICH_GPG_PASSPHRASE"))
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

            // build cli jar and docker image
            log("Building jar and docker image")
            Docker.enable()
            maven.exec("-B", "-q", "-Pbootstrap", "install", "-DskipTests")

            val imageVersion = cd("kannich-builder-image") {
                maven.getProjectVersion()
            }

            val imageBaseName = "derkork/kannich"
            val kannichImage = "$imageBaseName:$imageVersion"
            // run trivy on docker image to detect vulnerabilities
            log("Checking for vulnerabilities in docker image: $kannichImage")

            artifacts(On.SUCCESS_OR_FAILURE) {
                includes("trivy-docker-results.html")
            }

            val home = trivy.home()
            trivy.exec(
                "image",
                kannichImage,
                "--exit-code", "1",
                "--exit-on-eol", "1",
                "--severity", "CRITICAL",
                "--no-progress",
                "--format", "template",
                "--template", "@$home/contrib/html.tpl", "-o", "trivy-docker-results.html"
            )


            if (!dryRun) {
                log("Publishing docker image to docker hub")
                Docker.login(dockerUsername, dockerPassword)
                Docker.exec("push", kannichImage)
                // also push to the "latest" tag if desired
                if (setLatest) {
                    log("Setting latest tag")
                    Docker.exec("tag", kannichImage, "$imageBaseName:latest")
                    Docker.exec("push", "$imageBaseName:latest")
                }

                log("Publishing to Maven Central")
                withEnv(mapOf("MAVEN_GPG_PASSPHRASE" to gpgPassphrase)) {
                    maven.exec("-B", "-Prelease", "deploy", "-DskipTests")
                }
            } else {
                log("Dry run: not pushing.")
            }
        }
    }

    execution("dependency-check", "Verifies dependencies have no vulnerabilities") {
        job {
            val poms = Fs.glob("*/pom.xml")
            val home = trivy.home()

            val failures = poms.mapNotNull {
                val path = it.substringBeforeLast("/pom.xml")
                cd (path) {
                    Fs.mkdir("target")
                    val success = allowFailure {
                        trivy.exec(
                            "fs",
                            "--quiet",
                            "--scanners",
                            "vuln",
                            ".",
                            "--severity", "CRITICAL,HIGH",
                            "--ignore-unfixed",
                            "--exit-code", "1",
                            "--format",
                            "template",
                            "--template",
                            "@$home/contrib/html.tpl", "-o", "target/report.html"
                        )
                    }


                    if (!success) path else null
                }
            }

            artifacts(On.SUCCESS_OR_FAILURE) {
                includes("*/target/report.html")
            }


            if(failures.isNotEmpty()) {
                fail("Vulnerabilities found in dependencies: ${failures.joinToString(", ")}")
            }
        }
    }

    execution("clear-cache", "Clears the Kannich cache.") {
        job {
            Cache.clear()
        }
    }

    execution("smoke-test", "Runs a set of smoke tests to verify things work in general.") {
        job {
            val maven = Maven("3.9.6", java)
            val helm = Helm("3.19.4")
            java.exec("--version")
            maven.exec("--version")
            trivy.exec("version")
            helm.exec("version")

            Docker.enable()
            // try if we can run docker with a mount in the current work dir.
            Fs.write("test.txt", "Hello world!")
            val currentFolder = Fs.resolve("")
            Docker.exec("run", "-v", "$currentFolder:/data", "alpine", "cat", "/data/test.txt")

        }
    }
}
