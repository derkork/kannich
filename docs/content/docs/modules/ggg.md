+++
title = "GGG"
weight = 3
+++

## Overview

The GGG module provides [GGG (Godot Goodie Grabber)](https://godotneers.github.io/ggg) for your Kannich pipeline. GGG is a tool that manages Godot versions and Godot addons - similar to what UV does for Python projects. It uses a `ggg.toml` file to determine which Godot version to use and to resolve addon dependencies. Kannich downloads the requested GGG version automatically and caches both the tool itself and any assets GGG downloads, so subsequent runs are fast.

> **Architecture support:** GGG currently only runs on `amd64` architectures.

Add the module to your `.kannichfile.main.kts`:

```kotlin
@file:DependsOn("dev.kannich:kannich-ggg:{{ version(module="kannich-ggg") }}")
```

You can always find the latest version on the [Module Versions](@/docs/modules/_index.md) page.

## Basic Usage

Instantiate `Ggg` with the version you want, then call `exec()` with any arguments you'd pass to the `ggg` command. GGG reads the required Godot version and addons from your `ggg.toml` file:

```kotlin
import dev.kannich.ggg.Ggg
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    val ggg = Ggg("0.4.0")

    execution("sync", "Installs Godot and resolves addon dependencies") {
        job {
            ggg.exec("sync")
        }
    }

    execution("run", "Runs the project in Godot") {
        job {
            ggg.exec("run")
        }
    }
}
```

`ggg sync` downloads the Godot version specified in `ggg.toml` and installs all declared addons. `ggg run` launches Godot with the project.