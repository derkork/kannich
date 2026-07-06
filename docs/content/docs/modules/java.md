+++
title = "Java"
weight = 5
+++

## Overview

The Java module manages a JDK installation for your Kannich pipeline. It downloads the requested Java version from [Eclipse Temurin](https://adoptium.net/) automatically, caches it, and makes it available to other modules that need a JDK - most commonly [Maven](@/docs/modules/maven.md). It supports both `amd64` and `aarch64` architectures.

Add the module to your `.kannichfile.main.kts`:

```kotlin
@file:DependsOn("dev.kannich:kannich-java:{{ version(module="kannich-java") }}")
```

You can always find the latest version on the [Module Versions](@/docs/modules/_index.md) page.

## Basic Usage

Instantiate `Java` with the major version number you want. You typically declare it at the top of the pipeline so it can be shared across executions:

```kotlin
import dev.kannich.java.Java
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    val java = Java("21")

    execution("run", "Runs the application") {
        job {
            java.exec("-jar", "app.jar")
        }
    }
}
```

`java.exec()` runs the `java` binary with the given arguments and sets `JAVA_HOME` automatically before running.

In practice the Java module is most often used as a dependency for other tool modules rather than called directly - for example, `Maven` requires a `Java` instance to know which JDK to use. See the [Maven module](@/docs/modules/maven.md) for an example of this.

## Getting JAVA_HOME

If you need the path to the JDK installation - for example to pass it to a tool that isn't Java-aware - call `getInstallPath()`:

```kotlin
execution("build", "Builds the project") {
    job {
        val javaHome = java.getInstallPath()
        withEnv("JAVA_HOME" to javaHome) {
            Shell.exec("some_tool")
        }
    }
}
```
