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

    val test = job("Test", { include("**/target/surefire-reports/**") }) {
        maven.exec("-B", "test")
    }

    val packageJar = job("Package", { include("**/target/**/*.jar") }) {
        maven.exec("-B", "package", "-DskipTests")
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
