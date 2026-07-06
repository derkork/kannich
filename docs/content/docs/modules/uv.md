+++
title = "UV"
weight = 12
+++

## Overview

The UV module provides [UV](https://docs.astral.sh/uv/) for your Kannich pipeline. UV is a fast Python package and project manager that also handles Python version management - you do not need a separate Python installation. Kannich downloads the requested UV version automatically and caches both the package cache and any Python versions UV installs, so subsequent runs are fast. Both `amd64` and `aarch64` architectures are supported.

Add the module to your `.kannichfile.main.kts`:

```kotlin
@file:DependsOn("dev.kannich:kannich-uv:{{ version(module="kannich-uv") }}")
```

You can always find the latest version on the [Module Versions](@/docs/modules/_index.md) page.

## Basic Usage

Instantiate `Uv` with the version you want, then call `exec()` with any arguments you'd pass to the `uv` command. UV reads the required Python version from your `pyproject.toml` or `.python-version` file and installs it automatically if it is not already cached:

```kotlin
import dev.kannich.uv.Uv
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    val uv = Uv("0.10.7")

    execution("test", "Installs dependencies and runs the test suite") {
        job {
            artifacts(On.SUCCESS_OR_FAILURE) {
                includes("reports/**")
            }

            uv.exec("sync")
            uv.exec("run", "pytest", "--junitxml=reports/results.xml")
        }
    }

    execution("build", "Builds the Python package") {
        job {
            artifacts {
                includes("dist/**")
            }

            uv.exec("build")
        }
    }
}
```

`uv sync` installs the project's dependencies as defined in `pyproject.toml`. `uv run` executes a command inside the project's virtual environment, so there is no need to activate it manually.
