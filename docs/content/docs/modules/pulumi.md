+++
title = "Pulumi"
weight = 11
+++

## Overview

The Pulumi module provides Pulumi for your Kannich pipeline. It downloads the requested version automatically, caches it, and lets you provision and manage infrastructure as code. Both `amd64` and `aarch64` architectures are supported.

Add the module to your `.kannichfile.main.kts`:

```kotlin
@file:DependsOn("dev.kannich:kannich-pulumi:{{ version(module="kannich-pulumi") }}")
```

You can always find the latest version on the [Module Versions](@/docs/modules/_index.md) page.

## Basic Usage

Instantiate `Pulumi` with the version you want, then call `exec()` with any arguments you'd pass to the `pulumi` command. A typical pipeline logs in, selects a stack, and runs an update:

```kotlin
import dev.kannich.pulumi.Pulumi
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    val pulumi = Pulumi("3.252.0")

    execution("deploy", "Provisions infrastructure") {
        job {
            pulumi.exec("login", "--local")
            pulumi.exec("stack", "select", "prod")
            pulumi.exec("up", "--yes")
        }
    }
}
```

Provider credentials are picked up from environment variables the same way Pulumi normally does. For example `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` for the AWS provider, or `GOOGLE_APPLICATION_CREDENTIALS` for GCP.

## PULUMI_HOME and Plugin Caching

The module automatically manages `PULUMI_HOME` so you never have to set it yourself. For every job, Pulumi is given a `.kannich_pulumi` directory inside the job's working directory as its home. This directory is created on the first `exec()` call and reused for all following calls within the same job, so credentials set by e.g. `pulumi login` are available for the rest of the job.

Provider plugins are the one part of `PULUMI_HOME` that should persist across runs. The module handles this by symlinking `.kannich_pulumi/plugins` into the Kannich cache, keyed by architecture. The first time a provider is used it is downloaded. Later runs find it in the cache and skip the download. Providers for `amd64` and `arm64` are stored separately so mixed environments share the same cache safely.

Credentials, workspace state, and any other data Pulumi writes to `PULUMI_HOME` stay in the working directory and are never written into the Kannich tool cache.

## Pulumi Cloud vs Local Backend

When using Pulumi Cloud as the state backend, authenticate by setting the `PULUMI_ACCESS_TOKEN` environment variable rather than running `pulumi login` interactively. This is the standard approach in CI:

```kotlin
pipeline {
    val pulumi = Pulumi("3.252.0")

    execution("deploy", "Provisions infrastructure") {
        job {
            requireEnv("PULUMI_ACCESS_TOKEN")  // will fail the job if not set
            pulumi.exec("stack", "select", "org/project/prod")
            pulumi.exec("up", "--yes")
        }
    }
}
```

With `PULUMI_ACCESS_TOKEN` present in the environment, Pulumi authenticates automatically without writing credentials to disk.

For the local backend, pass `--local` to `pulumi login`:

```kotlin
pulumi.exec("login", "--local")
```

## Suppressing Output

`pulumi stack output` can print sensitive values. Pass `silent = true` to keep them out of the CI log:

```kotlin
val dbPassword = pulumi.exec("stack", "output", "--show-secrets", "dbPassword", silent = true).stdout.trim()
```
