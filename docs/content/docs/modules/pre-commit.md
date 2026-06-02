+++
title = "pre-commit"
weight = 7
+++

## Overview

The pre-commit module provides the [pre-commit](https://pre-commit.com/) framework for your Kannich pipeline. It downloads the requested version automatically as a self-contained executable - no Python installation is required. pre-commit reads your `.pre-commit-config.yaml` and runs the hooks defined there: formatters, linters, security scanners, and anything else in the pre-commit ecosystem. The hooks cache is stored in the Kannich cache directory and reused across runs.

Add the module to your `.kannichfile.main.kts`:

```kotlin
@file:DependsOn("dev.kannich:kannich-pre-commit:{{ version(module="kannich-pre-commit") }}")
```

You can always find the latest version on the [Module Versions](@/docs/modules/_index.md) page.

## Basic Usage

Instantiate `PreCommit` with the version you want, then call `exec()` with any arguments you'd pass to the `pre-commit` command. The most common invocation runs all configured hooks against every file in the repository:

```kotlin
import dev.kannich.precommit.PreCommit
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    val preCommit = PreCommit("4.0.1")

    execution("lint", "Runs all pre-commit hooks") {
        job {
            preCommit.exec("run", "--all-files")
        }
    }
}
```

To run a specific hook rather than all of them, pass its id:

```kotlin
preCommit.exec("run", "trailing-whitespace", "--all-files")
```
