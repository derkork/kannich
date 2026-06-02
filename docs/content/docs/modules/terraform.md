+++
title = "Terraform"
weight = 9
+++

## Overview

The Terraform module provides HashiCorp Terraform for your Kannich pipeline. It downloads the requested version automatically, caches it, and lets you provision and manage infrastructure as code. Both `amd64` and `aarch64` architectures are supported.

Add the module to your `.kannichfile.main.kts`:

```kotlin
@file:DependsOn("dev.kannich:kannich-terraform:{{ version(module="kannich-terraform") }}")
```

You can always find the latest version on the [Module Versions](@/docs/modules/_index.md) page.

## Basic Usage

Instantiate `Terraform` with the version you want, then call `exec()` with any arguments you'd pass to the `terraform` command. A typical pipeline runs `init`, then `plan`, then `apply`:

```kotlin
import dev.kannich.terraform.Terraform
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    val terraform = Terraform("1.11.0")

    execution("deploy", "Provisions infrastructure") {
        job {
            terraform.exec("init")
            terraform.exec("apply", "-auto-approve")
        }
    }
}
```

Note the `-auto-approve` flag - Terraform normally prompts for confirmation before applying changes, which would hang in a non-interactive CI environment.

Provider credentials are picked up from environment variables the same way Terraform normally does - for example `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` for the AWS provider, or `GOOGLE_APPLICATION_CREDENTIALS` for GCP. A common pattern when combined with the [AWS CLI module](@/docs/modules/aws-cli.md) is to use a CI service account with minimal permissions to assume a dedicated deployment role, then pass the resulting temporary credentials to Terraform via `withEnv`:

```kotlin
pipeline {
    val aws = AwsCli("2.17.44")
    val terraform = Terraform("1.11.0")

    execution("deploy", "Provisions infrastructure") {
        job {
            val roleArn = requireEnv("DEPLOY_ROLE_ARN")

            val credentials = aws.exec(
                "sts", "assume-role",
                "--role-arn", roleArn,
                "--role-session-name", "kannich-deploy",
                "--query", "[Credentials.AccessKeyId, Credentials.SecretAccessKey, Credentials.SessionToken]",
                "--output", "text",
                silent = true
            ).stdout.trim().split("\t")

            withEnv(
                "AWS_ACCESS_KEY_ID" to credentials[0],
                "AWS_SECRET_ACCESS_KEY" to credentials[1],
                "AWS_SESSION_TOKEN" to credentials[2]
            ) {
                terraform.exec("init")
                terraform.exec("apply", "-auto-approve")
            }
        }
    }
}
```

The `--query` flag extracts the three credential fields into a tab-separated line, avoiding the need to parse JSON. The temporary credentials are scoped to the `withEnv` block and only live for the duration of the Terraform run.

## Suppressing Output

`terraform plan` and `terraform output` can print sensitive values. Pass `silent = true` to keep them out of the CI log:

```kotlin
val output = terraform.exec("output", "-raw", "db_password", silent = true).stdout
```
