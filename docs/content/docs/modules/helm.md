+++
title = "Helm"
weight = 3
+++

## Overview

The Helm module provides the Helm package manager for Kubernetes in your Kannich pipeline. It downloads the requested Helm version automatically, caches it, and lets you install, upgrade, and manage Kubernetes applications packaged as Helm charts. Both `amd64` and `aarch64` architectures are supported.

Add the module to your `.kannichfile.main.kts`:

```kotlin
@file:DependsOn("dev.kannich:kannich-helm:{{ version(module="kannich-helm") }}")
```

You can always find the latest version on the [Module Versions](@/docs/modules/_index.md) page.

## Basic Usage

Instantiate `Helm` with the version you want, then call `exec()` with any arguments you'd pass to the `helm` command:

```kotlin
import dev.kannich.helm.Helm
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    val helm = Helm("3.14.0")

    execution("deploy", "Deploys the application to Kubernetes") {
        job {
            helm.exec("repo", "add", "my-repo", "https://charts.example.com")
            helm.exec("repo", "update")
            helm.exec("upgrade", "--install", "my-release", "my-repo/my-chart",
                "--set", "image.tag=1.2.3")
        }
    }
}
```

Helm picks up Kubernetes credentials from the standard `KUBECONFIG` environment variable or from `~/.kube/config`. In a CI environment you typically inject the kubeconfig as a secret and write it to a file before running Helm commands:

```kotlin
execution("deploy", "Deploys the application to Kubernetes") {
    job {
        val kubeconfig = requireEnv("KUBECONFIG_CONTENTS")
        val kubeconfigPath = "${Fs.mktemp()}/kubeconfig"
        Fs.write(kubeconfigPath, kubeconfig)

        helm.exec("upgrade", "--install", "my-release", "my-repo/my-chart",
            "--kubeconfig", kubeconfigPath,
            "--set", "image.tag=1.2.3")
    }
}
```
