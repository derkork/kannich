@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-maven:0.13.0")
@file:DependsOn("dev.kannich:kannich-java:0.10.0")
@file:DependsOn("dev.kannich:kannich-trivy:0.10.0")


import dev.kannich.java.Java
import dev.kannich.maven.Maven
import dev.kannich.stdlib.*
import dev.kannich.tools.*
import dev.kannich.trivy.Trivy

pipeline {
    val java = Java("21")
    val trivy = Trivy("0.69.3")

    suspend fun JobScope.collectModuleVersions(maven: Maven): Map<String, String> {
        val versions = mutableMapOf<String, String>()

        val items = maven.exec(
            "-q", "install",
            "exec:exec",
            "-Dexec.executable=echo",
            "-Dexec.args=\${project.groupId}:\${project.artifactId}:\${project.version}",
            "-pl", "!kannich-cli,!kannich-runtime",
            "-DskipTests"
        ).stdout.lines()

        for (item in items) {
            val coordinates = item.substringBeforeLast(":")
            val version = item.substringAfterLast(":")
            if (coordinates.isBlank() || version.isBlank()) {
                continue
            }
            versions[coordinates] = version
        }

        log("Collected module versions: $versions")

        return versions
    }

    suspend fun JobScope.setupMavenForDeployment(): Maven {
        val sonatypeUsername = requireEnv("KANNICH_SONATYPE_USERNAME")
        val sonatypePassword = secret(requireEnv("KANNICH_SONATYPE_PASSWORD"))
        return Maven("3.9.6", java) {
            server("ossrh") {
                username = sonatypeUsername
                password = sonatypePassword
            }
        }
    }


    execution("release-module", "Description releases a single module") {
        job {
            val gpgKey = requireEnv("KANNICH_GPG_KEY")
            val gpgPassphrase = secret(requireEnv("KANNICH_GPG_PASSPHRASE"))
            val moduleName = requireEnv("KANNICH_MODULE")

            Gpg.importKey(gpgKey)

            val maven = setupMavenForDeployment()

            maven.exec("-B", "-q", "clean", "install", "-DskipTests")

            log("Publishing to Maven Central")
            withEnv(mapOf("MAVEN_GPG_PASSPHRASE" to gpgPassphrase)) {
                if (moduleName != "*") {
                    maven.exec("-B", "-Prelease", "deploy", "-DskipTests", "-pl", moduleName)
                }
                else {
                    maven.exec("-B", "-Prelease", "deploy", "-DskipTests")
                }
            }
        }
    }

    execution("release", "Releases Kannich to Docker Hub and Maven Central") {
        job {
            val dockerUsername = requireEnv("KANNICH_DOCKER_USERNAME")
            val dockerPassword = secret(requireEnv("KANNICH_DOCKER_PASSWORD"))
            val gpgKey = requireEnv("KANNICH_GPG_KEY")
            val gpgPassphrase = secret(requireEnv("KANNICH_GPG_PASSPHRASE"))
            val setLatest = getEnvFlag("KANNICH_SET_LATEST") ?: true
            val dryRun = getEnvFlag("KANNICH_DRY_RUN") ?: false

            val maven = setupMavenForDeployment()
            Gpg.importKey(gpgKey)

            Docker.enable()
            maven.exec("-B", "-q", "-Pbootstrap", "clean", "install", "-DskipTests")

            val imageVersion = cd("kannich-builder-image") {
                maven.getProjectVersion()
            }

            val imageBaseName = "derkork/kannich"
            val kannichImage = "$imageBaseName:$imageVersion"
            val localImage = "localhost:5000/kannich:$imageVersion"

            // run trivy on the local registry image before publishing
            log("Checking for vulnerabilities in docker image: $localImage")

            artifacts(On.SUCCESS_OR_FAILURE) {
                includes("trivy-docker-results.html")
            }

            val home = trivy.home()
            trivy.exec(
                "image",
                localImage,
                "--insecure",
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

                // imagetools create copies OCI-format blobs as-is, but Docker Hub rejects OCI config blobs (400).
                // skopeo converts to Docker schema2 on the fly during the cross-registry copy.
                suspend fun skopeoCopy(dest: String) {
                    Docker.exec(
                        "run", "--rm", "--net=host",
                        "-v", "/root/.docker/config.json:/tmp/auth.json:ro",
                        "quay.io/skopeo/stable@sha256:5ece005de716d48ae6097afbdcd09c0fa19967623263aa4a16c19ceb8580191f",
                        "copy", "--all",
                        "--authfile", "/tmp/auth.json",
                        "--src-tls-verify=false",
                        "docker://$localImage", "docker://$dest"
                    )
                }

                skopeoCopy(kannichImage)
                if (setLatest) {
                    log("Setting latest tag")
                    skopeoCopy("$imageBaseName:latest")
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

    execution("update-test-versions", "Updates @file:DependsOn versions in all .tests.main.kts files to match current module versions") {
        job {
            val maven = Maven("3.9.6", java)
            val versions = collectModuleVersions(maven)

            val testFiles = Fs.glob("**/.tests.main.kts")
            for (testFile in testFiles) {
                var content = Fs.readAsString(testFile)
                var changed = false
                for ((artifactId, version) in versions) {
                    val regex = Regex("""(@file:DependsOn\("${Regex.escape(artifactId)}:)[^"]+("\))""")
                    val updated = regex.replace(content) { match ->
                        "${match.groupValues[1]}$version${match.groupValues[2]}"
                    }
                    if (updated != content) {
                        content = updated
                        changed = true
                    }
                }
                if (changed) {
                    log("Updated versions in $testFile")
                    Fs.write(testFile, content)
                }
            }

            artifacts {
                includes("**/.tests.main.kts")
            }
        }
    }

    execution("update-docs-versions", "Extracts module versions and updates documentation") {
        job {
            val maven = Maven("3.9.6", java)
            val versions = collectModuleVersions(maven)

            // Generate JSON file for documentation
            val json = "{${versions.entries.toList().sortedBy { it.key }.joinToString { "\"${it.key.substringAfterLast(":")}\": \"${it.value}\"" }}}"

            Fs.write("docs/static/versions.json", json)

            artifacts {
                includes("docs/static/versions.json")
            }
        }
    }
}
