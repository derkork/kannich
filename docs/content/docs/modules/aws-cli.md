+++
title = "AWS CLI"
weight = 1
+++

## Overview

The AWS CLI module provides AWS CLI v2 for your Kannich pipeline. It downloads the requested version automatically, caches it, and makes `aws` available for interacting with AWS services - deploying infrastructure, pushing container images to ECR, syncing files to S3, and so on. Both `amd64` and `aarch64` architectures are supported.

Add the module to your `.kannichfile.main.kts`:

```kotlin
@file:DependsOn("dev.kannich:kannich-aws-cli:{{ version(module="kannich-aws-cli") }}")
```

You can always find the latest version on the [Module Versions](@/docs/modules/_index.md) page.

## Basic Usage

Instantiate `AwsCli` with the version you want, then call `exec()` with any arguments you'd pass to the `aws` command:

```kotlin
import dev.kannich.awscli.AwsCli
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    val aws = AwsCli("2.17.44")

    execution("deploy", "Syncs build output to S3") {
        job {
            aws.exec("s3", "sync", "dist/", "s3://my-bucket/")
        }
    }
}
```

AWS credentials and region are picked up from environment variables the same way the AWS CLI normally does - `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_DEFAULT_REGION`, and so on. Pass these into the pipeline via `getEnv()` or `requireEnv()`, or inject them as CI secrets.

## Suppressing Output

Some `aws` commands print sensitive information - tokens, session credentials, account IDs - that you don't want appearing in CI logs. Pass `silent = true` to suppress all output from that call:

```kotlin
val identity = aws.exec("sts", "get-caller-identity", silent = true).stdout
```

This is also useful when calling AWS to retrieve a value mid-pipeline that you then use internally, where the raw JSON output would just be noise in the log.
