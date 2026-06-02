+++
title = "Quarkus"
weight = 8
+++

> **Experimental:** This module is in an early state and may change significantly or be removed in a future release. Use it with that in mind.

## Overview

The Quarkus module extends the [Maven module](@/docs/modules/maven.md) with a helper for building native Quarkus container images. It adds a `quarkusNativeBuild()` function directly to your `Maven` instance. The native compilation runs inside a builder container, so no local GraalVM installation is required - only Docker. The resulting image is built locally using Jib but not pushed; you push it separately with your registry tooling.

Add the module to your `.kannichfile.main.kts`:

```kotlin
@file:DependsOn("dev.kannich:kannich-quarkus:{{ version(module="kannich-quarkus") }}")
```

You can always find the latest version on the [Module Versions](@/docs/modules/_index.md) page.

## Basic Usage

Import the module and call `quarkusNativeBuild()` on your `Maven` instance:

```kotlin
import dev.kannich.java.Java
import dev.kannich.maven.Maven
import dev.kannich.quarkus.*
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    val java = Java("21")
    val maven = Maven("3.9.6", java)

    execution("build", "Builds a native Quarkus container image") {
        job {
            maven.quarkusNativeBuild(
                name = "my-service",
                tag = getEnv("BUILD_NUMBER") ?: "latest"
            )
        }
    }
}
```

`quarkusNativeBuild()` accepts the following parameters:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `name` | `"output"` | The container image name |
| `tag` | `"latest"` | The container image tag |
| `labels` | `emptyMap()` | Additional labels to apply to the container image |

Proxy settings are forwarded to the native build container automatically if they are configured in the Kannich environment.
