+++
title = "Controlling the Environment"
weight = 4
+++

## Introduction

This guide covers how to read environment variables passed to a pipeline, how to set environment variables for external tools, and how to make tools available on the PATH.

## Reading Environment Variables

All pipeline contexts - inside `pipeline {}`, `stage {}`, and `job {}` blocks - provide `getEnv` and `requireEnv` for reading environment variables. Users pass extra variables to a build with `-e KEY=VALUE` on the command line.

`getEnv` returns `null` when the variable is not set:

```kotlin
val registry = getEnv("DOCKER_REGISTRY") ?: "registry.example.com"
```

`requireEnv` aborts the job with a descriptive error when the variable is missing, which is the right choice for anything the build genuinely cannot proceed without:

```kotlin
val token = requireEnv("DEPLOY_TOKEN")
```

Typed variants are available for common cases:

```kotlin
val port = getEnvInt("SERVER_PORT") ?: 8080
val dryRun = getEnvFlag("DRY_RUN") ?: false
val port = requireEnvInt("SERVER_PORT")
val dryRun = requireEnvFlag("DRY_RUN")
```

`hasEnv` checks presence without reading the value:

```kotlin
if (hasEnv("CI")) {
    log("Running in CI")
}
```

## Setting Environment Variables with `withEnv`

Inside a job you can override environment variables for any block of code using `withEnv`. The change is scoped to the block - when it returns, the previous environment is restored. This works the same way as `cd {}` for working directories.

Pass variables as named pairs:

```kotlin
withEnv("GRADLE_OPTS" to "-Xmx4g", "JAVA_TOOL_OPTIONS" to "-Dfile.encoding=UTF-8") {
    gradle.exec("build")
}
```

`withEnv` also affects what `getEnv` returns inside the block. Once the block ends, `getEnv` returns the original value again:

```kotlin
// getEnv("DEPLOY_TARGET") returns "staging" here (set by the caller)
withEnv("DEPLOY_TARGET" to "production") {
    val target = getEnv("DEPLOY_TARGET")  // "production"
    log("Deploying to $target")
}
// getEnv("DEPLOY_TARGET") returns "staging" again
```

To unset a variable for the duration of the block, pass `null` as its value:

```kotlin
withEnv("CI" to null) {
    // tools that behave differently when CI is set will not see it here
    gradle.exec("test")
}
```

`withEnv` blocks can be nested. Each layer only changes the variables it names; everything else is inherited:

```kotlin
withEnv("AWS_REGION" to "eu-west-1") {
    withEnv("AWS_PROFILE" to "staging") {
        // Both AWS_REGION and AWS_PROFILE are set here
        aws.exec("s3", "sync", "dist/", "s3://my-bucket/")
    }
    // AWS_PROFILE is gone, AWS_REGION is still eu-west-1
}
```

## Making Tools Available with `withTools`

`withTools` prepends a tool's installation directory to the PATH for the duration of the block. This is useful when a build plugin or script spawns a program by name and expects to find it on the PATH, rather than accepting a path as an argument.

```kotlin
val trivy = Trivy("0.69.3")
withTools(trivy) {
    maven.exec("verify")  // maven plugins that invoke `trivy` will find it
}
```

Multiple tools can be passed in a single call:

```kotlin
withTools(Trivy("0.69.3"), Helm("3.14.0")) {
    maven.exec("verify")
}
```

`withTools` is implemented on top of `withEnv`, so it follows the same scoping rules. Outside the block, the PATH is restored to its previous value.
