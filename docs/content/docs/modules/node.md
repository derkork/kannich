+++
title = "Node.js"
weight = 7
+++

## Overview

The Node.js module provides Node.js for your Kannich pipeline. It downloads the requested version automatically, caches it, and exposes the `node`, `npm`, and `npx` binaries for running JavaScript tools and build systems. Both `amd64` and `aarch64` architectures are supported.

Add the module to your `.kannichfile.main.kts`:

```kotlin
@file:DependsOn("dev.kannich:kannich-node:{{ version(module="kannich-node") }}")
```

You can always find the latest version on the [Module Versions](@/docs/modules/_index.md) page.

## Basic Usage

Instantiate `Node` with the version you want. The `Node` instance gives you access to three tools: `node` itself, plus `node.npm` and `node.npx` as sub-tools. Each has its own `exec()` method:

```kotlin
import dev.kannich.node.Node
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    val node = Node("22.14.0")

    execution("build", "Installs dependencies and builds the project") {
        job {
            artifacts {
                includes("dist/**")
            }

            node.npm.exec("ci")
            node.npm.exec("run", "build")
        }
    }

    execution("test", "Runs the test suite") {
        job {
            artifacts(On.SUCCESS_OR_FAILURE) {
                includes("test-results/**")
            }

            node.npm.exec("ci")
            node.npm.exec("test")
        }
    }
}
```

## Sub-tools

`node.npm` and `node.npx` are sub-tools that run `npm` and `npx` respectively. They automatically ensure Node.js is on the `PATH` before running, which is required for `npm` and `npx` to work correctly. Use them exactly like the main `exec()` - pass any arguments you'd normally use on the command line:

```kotlin
// Run a script defined in package.json
node.npm.exec("run", "lint")

// Execute a package without installing it globally
node.npx.exec("tsc", "--noEmit")
```

You can also run the `node` binary directly for one-off scripts:

```kotlin
node.exec("--eval", "console.log(process.version)")
```
