@file:DependsOn("dev.kannich:kannich-stdlib:0.1.0-SNAPSHOT")
@file:DependsOn("dev.kannich:kannich-maven:0.1.0-SNAPSHOT")
@file:DependsOn("dev.kannich:kannich-java:0.1.0-SNAPSHOT")
@file:DependsOn("dev.kannich:kannich-trivy:0.1.0-SNAPSHOT")
@file:DependsOn("dev.kannich:kannich-helm:0.1.0-SNAPSHOT")


import dev.kannich.helm.Helm
import dev.kannich.java.Java
import dev.kannich.maven.Maven
import dev.kannich.stdlib.*
import dev.kannich.trivy.Trivy

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

    val testModules = job("test-modules") {
        java.exec("--version")
        maven.exec("--version")

        val helm = Helm("3.19.4")
        helm.exec("version", "--short")

        val trivy = Trivy("0.68.2")
        trivy.exec("--version")
    }

    val packageJar = job("Package") {
        maven.exec("-B", "package", "-DskipTests")

        artifacts {
            includes("kannich-*/target/*.jar")
            excludes("kannich-*/target/*-sources.jar")
            excludes("kannich-*/target/original-*.jar")
        }
    }

    execution("deploy") {
        job("deploy") {
            if (!env.get)

            // build jars and
            maven.exec("-B", "-Pbootstrap", "install")
        }
    }


    execution("test") {
        job(packageJar)
    }

    execution("layers") {
        job("l1") {
            fs.write("foo.txt", "Hey this is some text!!!!")
        }
        job ("l2") {
            fs.write("foo.txt", "With even more stuff in it!", true)
            shell.execShell("cat foo.txt")
            artifacts {
                includes("foo.txt")
            }
        }

    }

}
