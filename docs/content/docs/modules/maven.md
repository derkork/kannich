+++
title = "Maven"
weight = 6
+++

## Overview

The Maven module lets you build Java projects with Apache Maven inside your Kannich pipeline. It downloads the requested Maven version automatically (and caches it), pairs it with a Java installation from the [Java module](@/docs/modules/java.md), and runs Maven goals in an isolated container. Maven's local repository is also cached between runs so you don't re-download your dependencies on every build.

Add the module to your `.kannichfile.main.kts`:

```kotlin
@file:DependsOn("dev.kannich:kannich-maven:{{ version(module="kannich-maven") }}")
```

You can always find the latest version on the [Module Versions](@/docs/modules/_index.md) page.

## Basic Usage

`Maven` requires a `Java` instance - this is how you tell it which JDK to use, and it lets you mix any Maven version with any Java version independently.

```kotlin
import dev.kannich.java.Java
import dev.kannich.maven.Maven
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    val java = Java("21")
    val maven = Maven("3.9.6", java)

    execution("build", "Compiles and packages the application") {
        job {
            artifacts {
                includes("target/*.jar")
            }

            maven.exec("clean", "package", "-DskipTests")
        }
    }

    execution("test", "Runs the test suite") {
        job {
            artifacts(On.SUCCESS_OR_FAILURE) {
                includes("target/surefire-reports/**")
            }

            maven.exec("test")
        }
    }
}
```

`maven.exec()` takes any Maven goals and options you'd normally pass on the command line. The Maven module automatically sets `JAVA_HOME`, `MAVEN_HOME`, and `M2_HOME` before running Maven, so you don't need to configure those yourself.

## Server Configuration

If your pipeline publishes artifacts or resolves dependencies from a private repository, you need to supply credentials. Pass a configuration block to `Maven` and declare one `server` entry per repository. The server `id` must match the `<id>` you've used in your `pom.xml` `<distributionManagement>` or `<repository>` section.

```kotlin
val ciUsername = getEnv("CI_USERNAME") ?: ""
val ciPassword = requireEnv("CI_PASSWORD")

val maven = Maven("3.9.6", java) {
    server("my-repo") {
        username = ciUsername
        password = ciPassword
    }
}
```

Kannich generates a `settings.xml` from this configuration and passes it to Maven automatically. The file is written to a temporary location inside the job's working directory and deleted when the job finishes.

### HTTP Headers

Some repository managers (for example GitHub Packages when accessed via a token) require custom HTTP headers instead of - or in addition to - username/password credentials. You can add headers to any server block:

```kotlin
val maven = Maven("3.9.6", java) {
    server("github-packages") {
        header("Authorization", "Bearer ${requireEnv("GITHUB_TOKEN")}")
    }
}
```

## Proxy Settings

If Kannich itself is running behind an HTTP or HTTPS proxy, the Maven module picks up the proxy configuration automatically and writes it into the generated `settings.xml`. No extra configuration is needed in your pipeline - if the JVM running Kannich has `http.proxyHost` / `https.proxyHost` system properties set (which the Kannich launcher handles when a proxy is detected), Maven will use them.

## Reading the Project Version

You can read the Maven project version from your `pom.xml` without having to parse XML yourself:

```kotlin
execution("release", "Tags and publishes the release") {
    job {
        val version = maven.getProjectVersion()
        log("Building version $version")
        maven.exec("deploy", "-P", "release")
    }
}
```

More generally, `maven.evaluateExpression("some.expression")` evaluates any Maven expression and returns its value as a string, the same way `mvn help:evaluate` does.
