+++
title = "Running Programs"
weight = 3
+++

## Introduction

This guide covers how to run external programs from a pipeline, from simple command invocations to capturing and processing their output.

## Running Commands with `Shell`

`Shell` is the low-level tool for executing arbitrary programs. `Shell.exec` takes a command and any number of arguments:

```kotlin
Shell.exec("git", "fetch", "--tags")
Shell.exec("docker", "build", "-t", "myapp:latest", ".")
```

Arguments are passed directly to the process, so no shell quoting or escaping is needed. You can, also pass kotlin variables as arguments:

```kotlin
val version = "1.0.0"
Shell.exec("git", "tag", version)
```

`Shell.exec` always returns an `ExecResult` and never aborts the job on a non-zero exit code - handling the result is up to you. To check whether a command succeeded, use the `success` property:

```kotlin
val result = Shell.exec("git", "diff", "--quiet")
if (!result.success) {
    log("Working tree has uncommitted changes (exit code ${result.exitCode})")
}
```

To read program output, use `result.stdout`:

```kotlin
val result = Shell.exec("git", "rev-parse", "HEAD")
val commitSha = result.stdout.trim()
```

`result.stderr` is available in the same way.

### Running a Shell Expression

`Shell.execShell` passes a string to `sh -c`, which lets you use pipes and redirections in a single call:

```kotlin
Shell.execShell("someprogram --flag | someotherprogram --flag")
```

This is an escape hatch for cases where a pipeline of tools is genuinely the simplest option. Since pipelines are Kotlin scripts, it is usually cleaner to capture stdout from one `Shell.exec` call and pass it as input to the next, keeping data in variables rather than in shell plumbing. If you find yourself reaching for `awk` or `sed`, consider doing the text processing in Kotlin instead.

## Running Tools

All built-in tools (`Java`, `AwsCli`, and others) expose the same `exec` method as `Shell`, so the calling convention is consistent across the codebase:

```kotlin
val java = Java("21")
java.exec("-jar", "myapp.jar", "--config", "config.yml")

val aws = AwsCli("2.17.44")
aws.exec("s3", "cp", "dist/myapp.tar.gz", "s3://my-bucket/releases/")
```

Unlike `Shell`, tool `exec` calls abort the job by default when the command exits with a non-zero code. This makes failure visible immediately without boilerplate checks on every call.

### Allowing a Command to Fail

Set `allowFailure = true` when you expect a command might fail and want to handle the result yourself:

```kotlin
val result = aws.exec("s3", "head-object",
    "--bucket", "my-bucket",
    "--key", "releases/myapp-$version.tar.gz",
    allowFailure = true
)
if (result.success) {
    log("Release $version already exists in S3, skipping upload")
} else {
    aws.exec("s3", "cp", "dist/myapp.tar.gz", "s3://my-bucket/releases/")
}
```

### Suppressing Output with `silent`

By default, everything a program prints goes to the job log. Pass `silent = true` to suppress it - the output is still available in `result.stdout` but will not appear in the logs. This is useful when a command produces sensitive data:

```kotlin
// fetches an auth token without printing it to the log
val result = aws.exec("codeartifact", "get-authorization-token",
    "--domain", "my-domain",
    "--query", "authorizationToken",
    "--output", "text",
    silent = true
)
val token = result.stdout.trim()
```

`silent` works the same way on `Shell.exec` and `Shell.execShell`.
