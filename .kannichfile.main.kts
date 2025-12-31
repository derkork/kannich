// Kannich self-build pipeline
// This file demonstrates how Kannich can build itself
//
// For IDE autocomplete: run 'mvn install' to put jars in local .m2/repository

@file:DependsOn("dev.kannich:kannich-stdlib:0.1.0-SNAPSHOT")
@file:DependsOn("dev.kannich:kannich-maven:0.1.0-SNAPSHOT")
@file:DependsOn("dev.kannich:kannich-java:0.1.0-SNAPSHOT")

import dev.kannich.java.Java
import dev.kannich.maven.Maven
import dev.kannich.stdlib.*

pipeline {
    val java = Java("21")
    val maven = Maven("3.9.6", java)

    val compile = job("Compile") {
        maven.exec("-B", "compile")
    }

    val test = job("Test") {
        maven.exec("-B", "test")

        artifacts {
            includes("**/target/surefire-reports/**")
        }
    }

    val packageJar = job("Package") {
        maven.exec("-B", "package", "-DskipTests")

        artifacts {
            includes("**/target/*.jar")
            excludes("**/target/*-sources.jar")
            excludes("**/target/original-*.jar")
        }
    }

    execution("fail") {
        sequentially {
            job("fail-job") {
                fail("This is designed to fail.")
            }
        }
    }

    execution("testenv") {
        sequentially {
            job("testenv") {
                shell.execShell("echo \$CI_DUMMY_VAR")
                shell.execShell("echo 'noot'")
            }
        }
    }

    execution("build") {
        sequentially {
            job(compile)
            job(test)
            job(packageJar)
        }
    }

    execution("quick-build") {
        sequentially {
            job(compile)
            job(packageJar)
        }
    }
}
