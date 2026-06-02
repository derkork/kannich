+++
title = "Google Cloud CLI"
weight = 2
+++

## Overview

The Google Cloud CLI module provides `gcloud` for your Kannich pipeline. It downloads the requested version automatically, caches it, and makes the full Google Cloud SDK available - deploying to Cloud Run, pushing images to Artifact Registry, managing GKE clusters, and anything else you'd do with `gcloud`. Both `amd64` and `aarch64` architectures are supported.

Add the module to your `.kannichfile.main.kts`:

```kotlin
@file:DependsOn("dev.kannich:kannich-gcloud-cli:{{ version(module="kannich-gcloud-cli") }}")
```

You can always find the latest version on the [Module Versions](@/docs/modules/_index.md) page.

## Basic Usage

Instantiate `GcloudCli` with the version you want, then call `exec()` with any arguments you'd pass to the `gcloud` command:

```kotlin
import dev.kannich.gcloud.GcloudCli
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    val gcloud = GcloudCli("490.0.0")

    execution("deploy", "Deploys the application to Cloud Run") {
        job {
            gcloud.exec("run", "deploy", "my-service", "--image", "gcr.io/my-project/my-image")
        }
    }
}
```

GCP credentials are typically provided via the `GOOGLE_APPLICATION_CREDENTIALS` environment variable pointing to a service account key file, or via Workload Identity when running on GCP infrastructure. Pass credentials into the pipeline via `getEnv()` or `requireEnv()`, or inject them as CI secrets.

## Suppressing Output

When a `gcloud` command returns sensitive data such as access tokens or service account keys, pass `silent = true` to keep it out of the CI log:

```kotlin
val token = gcloud.exec("auth", "print-access-token", silent = true).stdout
```
