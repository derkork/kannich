+++
title = "Quick Start"
weight = 1
+++

Get started with Kannich in 5 minutes. This guide will walk you through creating and running your first CI pipeline.

## Prerequisites

- **Docker**: Kannich runs all jobs in Docker containers. Make sure Docker is installed and running. This is the only prerequisite.

## Installation

Kannich uses a wrapper script (similar to Gradle's `gradlew`) that bootstraps itself automatically:

```bash
# Download the wrapper script (Bash)
curl -o kannichw https://raw.githubusercontent.com/derkork/kannich/main/kannichw
chmod +x kannichw
```

```powershell 
# Download the wrapper script (PowerShell)!
Invoke-WebRequest -Uri https://raw.githubusercontent.com/derkork/kannich/main/kannichw.ps1 -OutFile kannichw.ps1
```

The wrapper will then launch the Kannich docker image to execute your pipeline. Nothing gets installed on your system. Put the wrapper in your project root and commit it to your repository so it is available to all contributors and the CI system you're using.

## Create Your First Pipeline

Create a file named `.kannichfile.main.kts` in your project root:

```kotlin
@file:DependsOn("dev.kannich:kannich-stdlib:{{ version(module="kannich-stdlib") }}")
@file:DependsOn("dev.kannich:kannich-tools:{{ version(module="kannich-tools") }}")
@file:DependsOn("dev.kannich:kannich-maven:{{ version(module="kannich-maven") }}")
@file:DependsOn("dev.kannich:kannich-java:{{ version(module="kannich-java") }}")

import dev.kannich.java.Java
import dev.kannich.maven.Maven
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    // These are tools, that we want to use in our pipeline.
    // Kannich will automatically download and install them for us. They also get cached for future runs.
    val java = Java("21")
    // Note that Maven is a tool that requires a JDK, so we give the Java tool as a dependency.
    // This way we can combine any Java version with any Maven version (provided they work together).
    val maven = Maven("3.9.6", java)

    // this is an execution. It's like a job in a CI system.
    execution("build", "Build and test the project") {
        // an execution can have multiple jobs, though most will only have one.
        job {
            // Compile the project
            maven.exec("clean", "compile")

            // Run tests
            maven.exec("test")

            // Kannich will never modify files on your filesystem, so everything you want to keep
            // must be marked as an artifact. Kannich will copy these files back to your project directory.
            artifacts {
                includes("target/surefire-reports/**")
            }
        }
    }
}
```

## Run Your Pipeline

Execute the pipeline:

```bash
./kannichw build

# or on PowerShell

.\kannichw.ps1 build
```

Now Kannich will:

1. Download and cache the required tools (Java, Maven)
2. Create a Docker container with an isolated filesystem
3. Execute your jobs in order
4. Collect artifacts back to your local filesystem

## View Results

After execution:
- Build artifacts are copied to your project directory at their original paths.
- You can inspect the build output for any errors.

## Troubleshooting
Kannich will print out any errors it encounters to the console. Any call to tools that fails will abort the build. You 
can also use the `-v` flag to print out more verbose output. This will print out the full command of all executed tools 
including all console output.

