+++
title = "Trivy"
weight = 11
+++

## Overview

The Trivy module provides the [Trivy](https://trivy.dev/) security scanner for your Kannich pipeline. It downloads the requested version automatically, caches it, and keeps Trivy's vulnerability database in the Kannich cache so it doesn't re-download on every run. Trivy can scan container images, filesystems, and more. Both `amd64` and `aarch64` architectures are supported.

Add the module to your `.kannichfile.main.kts`:

```kotlin
@file:DependsOn("dev.kannich:kannich-trivy:{{ version(module="kannich-trivy") }}")
```

You can always find the latest version on the [Module Versions](@/docs/modules/_index.md) page.

## Scanning Dependencies

The `scanFs()` helper scans the current directory for vulnerable dependencies and writes an HTML report. It fails the job if any `CRITICAL` or `HIGH` severity vulnerabilities are found:

```kotlin
import dev.kannich.trivy.Trivy
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    val trivy = Trivy("0.69.3")

    execution("scan", "Checks dependencies for vulnerabilities") {
        job {
            artifacts(On.SUCCESS_OR_FAILURE) {
                includes("target/report.html")
            }

            trivy.scanFs()
        }
    }
}
```

`scanFs()` accepts two optional parameters:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `reportPath` | `"target/report.html"` | Path to write the HTML report |
| `severity` | `"CRITICAL,HIGH"` | Comma-separated severity levels to flag |

Declaring the artifact with `On.SUCCESS_OR_FAILURE` is important here - the report is most useful when the scan fails, which is also when Kannich would normally skip artifact collection.

## Scanning Container Images

For scanning container images, use `exec()` directly. Trivy ships with an HTML report template that you can reference via `getInstallPath()`:

```kotlin
execution("scan-image", "Scans a container image for vulnerabilities") {
    job {
        artifacts(On.SUCCESS_OR_FAILURE) {
            includes("trivy-report.html")
        }

        val trivyHome = trivy.getInstallPath()
        trivy.exec(
            "image",
            "--exit-code", "1",
            "--exit-on-eol", "1",
            "--severity", "CRITICAL",
            "--no-progress",
            "--format", "template",
            "--template", "@$trivyHome/contrib/html.tpl",
            "-o", "trivy-report.html",
            "myapp:latest"
        )
    }
}
```

`--exit-code 1` makes Trivy exit with a non-zero code when vulnerabilities are found, which causes Kannich to fail the job. `--exit-on-eol 1` does the same when the scanned image has reached end-of-life.
