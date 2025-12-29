// Kannich self-build pipeline
// This file demonstrates how Kannich can build itself
//
// For IDE autocomplete: run 'mvn install' to put jars in local .m2/repository

@file:DependsOn("dev.kannich:kannich-stdlib:0.1.0-SNAPSHOT")
@file:DependsOn("dev.kannich:kannich-maven:0.1.0-SNAPSHOT")
@file:DependsOn("dev.kannich:kannich-jvm:0.1.0-SNAPSHOT")

import dev.kannich.jvm.Java
import dev.kannich.maven.Maven
import dev.kannich.stdlib.*

pipeline {
    val java = Java("21")
    val maven = Maven("3.9.6", java)

    val compile = job("Compile") {
        maven.exec("compile")
    }

    val test = job("Test", { include("**/target/surefire-reports/**") }) {
        maven.exec("test")
    }

    val packageJar = job("Package", { include("kannich-cli/target/kannich-cli-*.jar") }) {
        maven.exec("package", "-DskipTests")
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
