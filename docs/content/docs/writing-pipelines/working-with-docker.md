+++
title = "Working with Docker"
weight = 3
+++

## Introduction

Kannich pipelines run inside a container image that includes a full Docker daemon. This is a separate, independent daemon from the one that runs the Kannich container itself, so you can build, push, and manage images without interfering with the host Docker environment.

The built-in `Docker` tool wraps the Docker CLI and handles common operations: building images, logging into registries, tagging, pushing, and running arbitrary Docker commands.

## Enabling Docker

The Docker daemon is **not started by default**. You must call `Docker.enable()` before any Docker work. Any daemon configuration must be done *before* that call.

```kotlin
job("Build Image") {
    Docker.enable()
    Docker.exec("build", "-t", "myapp:latest", ".")
}
```

`Docker.enable()` is idempotent: calling it when Docker is already running is a no-op. 

## Configuring the Docker Daemon

Pass a JSON string to `Docker.enable()` to write a custom `daemon.json` before the daemon starts. This lets you configure the built-in daemon independently of the host. When `Docker.enable()` is called with a different config than what the daemon is already running with, it restarts the daemon automatically to apply the new settings.

### Adding Insecure Registries

Use this when pushing to a private registry that serves over plain HTTP or uses a self-signed certificate:

```kotlin
job("Push to Private Registry") {
    Docker.enable("""
        {
            "insecure-registries": ["registry.internal:5000"]
        }
    """.trimIndent())
    Docker.exec("build", "-t", "registry.internal:5000/myapp:latest", ".")
    Docker.exec("push", "registry.internal:5000/myapp:latest")
}
```

## Running Docker Commands

`Docker.exec()` passes arguments directly to the `docker` CLI and fails the job if the command exits with a non-zero status.

```kotlin
Docker.exec("build", "-t", "myapp:latest", ".")
Docker.exec("run", "--rm", "myapp:latest", "./smoke-test.sh")
```

Suppress output for noisy commands:

```kotlin
Docker.exec("pull", "alpine:latest", silent = true)
```

## Logging into a Registry

`Docker.login()` authenticates with a Docker registry. The password is passed securely via stdin and masked in logs.

```kotlin
Docker.login(
    username = env("REGISTRY_USER"),
    password = env("REGISTRY_PASSWORD"),
    registry = "registry.internal:5000"
)
```

Omit `registry` to log into Docker Hub:

```kotlin
Docker.login(
    username = env("DOCKERHUB_USER"),
    password = env("DOCKERHUB_TOKEN")
)
```

## Building and Pushing an Image

A typical build-and-push pipeline:

```kotlin
job("Build and Push") {
    Docker.enable()
    Docker.login(
        username = env("REGISTRY_USER"),
        password = env("REGISTRY_PASSWORD"),
        registry = "registry.internal:5000"
    )
    Docker.exec("build", "-t", "myapp:${env("VERSION")}", ".")
    Docker.exec("push", "myapp:${env("VERSION")}")
}
```

## Tagging and Pushing

`Docker.tagAndPush()` combines `docker tag` and `docker push` into one call:

```kotlin
Docker.tagAndPush(
    localImage = "myapp:latest",
    targetImage = "registry.internal:5000/team/myapp:${env("VERSION")}"
)
```

This is useful when you build under a local name and need to promote the image to a remote registry under a different tag.

## Multi-Stage Example: Build, Test, Push

```kotlin
job("CI") {
    Docker.enable()
    Docker.login(
        username = env("REGISTRY_USER"),
        password = env("REGISTRY_PASSWORD"),
        registry = "registry.internal:5000"
    )

    // Build
    Docker.exec("build", "-t", "myapp:build", ".")

    // Run tests inside the image
    Docker.exec("run", "--rm", "myapp:build", "./run-tests.sh")

    // Push under the commit SHA
    val tag = "registry.internal:5000/myapp:${env("GIT_SHA")}"
    Docker.tagAndPush("myapp:build", tag)
}
```
