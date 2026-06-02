+++
title = "Your First Pipeline"
weight = 1
+++

## The Goal

You have a Java project and you want to automate two things: packaging a distributable JAR and running the test suite. By the end of this guide you'll have a pipeline with two executions - `build` and `test` - that you can run locally and hand off to any CI system unchanged.

## Setting Up the Pipeline File

All Kannich pipelines live in a file called `.kannichfile.main.kts` in the root of your project. This is a [Kotlin Script](https://kotlinlang.org/docs/custom-script-deps-tutorial.html) file, which means you get real IDE support. In **IntelliJ IDEA** (including the free Community Edition) you get full autocomplete, navigation, and error highlighting. In **VS Code** you get at least syntax highlighting via the Kotlin extension. At the top you declare which Kannich modules you need using `@file:DependsOn` annotations:

```kotlin
@file:DependsOn("dev.kannich:kannich-stdlib:{{ version(module="kannich-stdlib") }}")
@file:DependsOn("dev.kannich:kannich-tools:{{ version(module="kannich-tools") }}")
@file:DependsOn("dev.kannich:kannich-java:{{ version(module="kannich-java") }}")
@file:DependsOn("dev.kannich:kannich-maven:{{ version(module="kannich-maven") }}")
```

You can always find the latest version of every module on the [Module Versions](@/docs/modules/_index.md) page.

Then come the imports and the `pipeline { }` block, which is the outermost container for everything:

```kotlin
import dev.kannich.java.Java
import dev.kannich.maven.Maven
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    // tools and executions go here
}
```

## Your First Execution

An *execution* is a named entry point, the thing you ask Kannich to run by name. A `job { }` inside the execution is where the actual work happens. Most executions contain exactly one job.

Let's compile and package the project:

```kotlin
pipeline {
    val java = Java("21")
    val maven = Maven("3.9.6", java)

    execution("build", "Compiles and packages the application") {
        job {
            maven.exec("clean", "package", "-DskipTests")
        }
    }
}
```

Tools can be declared anywhere in the pipeline - at the top level, inside an execution, or even inside a job. Declaring them at the top is just a convenient convention when multiple executions share the same tool. You could just as well use different Java versions in different executions. Each tool exposes an `exec` method that runs the tool with the given arguments. Notice that `Maven` takes the `Java` instance as a parameter - this is how you tell Maven which Java version to use, and it lets you combine any Maven version with any Java version independently. Kannich downloads and caches all tools automatically - the only prerequisite on any machine is Docker.

You run this execution with:

```bash
./kannichw build
```

## Collecting Artifacts

Here's something important: **every job runs in an isolated container with its own filesystem**. Files produced during the build - JARs, reports, anything - exist only inside that container. To get them back you declare them as artifacts and Kannich copies them to your project directory when the job finishes.

There's a subtle but important point about *when* to declare artifacts. The `artifacts { }` block is **not** a step that runs at the end - it is a declaration that registers what should be collected. If you put it after a step that fails, Kannich never reaches it and collects nothing. The safe habit is to declare artifacts at the **top** of the job, before any build steps:

```kotlin
execution("build", "Compiles and packages the application") {
    job {
        artifacts {
            includes("target/*.jar")
        }

        maven.exec("clean", "package", "-DskipTests")
    }
}
```

Now even if `maven.exec` fails midway, Kannich still knows what to collect. The `includes` pattern uses ant-style glob syntax: `*` matches within a single directory level, `**` crosses directory boundaries.

## The Test Execution

Let's add a second execution for the tests. Having them separate means you can run `./kannichw test` independently, which is handy for a quick feedback loop without rebuilding the whole project:

```kotlin
execution("test", "Runs the test suite") {
    job {
        artifacts(On.SUCCESS_OR_FAILURE) {
            includes("target/surefire-reports/**")
        }

        maven.exec("test")
    }
}
```

Notice `On.SUCCESS_OR_FAILURE` on the `artifacts` call. Test reports are most useful when something *fails* - that's exactly when you need to see them. The default behaviour collects artifacts only on success, so passing `On.SUCCESS_OR_FAILURE` overrides that and ensures the reports always come back.

## Reading Environment Variables

Most pipelines need configuration from outside: API keys, version numbers, feature flags. Kannich gives you two functions for this:

- `getEnv("NAME")` - returns `String?`. Use it for optional configuration where a sensible default exists.
- `requireEnv("NAME")` - returns `String`, or immediately fails the job if the variable is not set. Use it for anything the pipeline genuinely cannot proceed without.

Here's both in action. A build number is optional and falls back to `"local"` when running on a developer machine. A deploy token is mandatory in any execution that publishes something:

```kotlin
execution("build", "Compiles and packages the application") {
    job {
        val buildNumber = getEnv("BUILD_NUMBER") ?: "local"

        artifacts {
            includes("target/*.jar")
        }

        log("Build number: $buildNumber")
        maven.exec("clean", "package", "-DskipTests", "-Dbuild.number=$buildNumber")
    }
}

execution("publish", "Publishes the artifact to the repository") {
    job {
        // requireEnv fails the job immediately if REPO_TOKEN is not set,
        // so we never accidentally try to publish without credentials
        val repoToken = requireEnv("REPO_TOKEN")
        // ...
    }
}
```

`log()` writes a message to the build output. It's useful for surfacing the values your pipeline actually ran with, which makes debugging much easier.

## The Complete Pipeline

Putting it all together:

```kotlin
@file:DependsOn("dev.kannich:kannich-stdlib:{{ version(module="kannich-stdlib") }}")
@file:DependsOn("dev.kannich:kannich-tools:{{ version(module="kannich-tools") }}")
@file:DependsOn("dev.kannich:kannich-java:{{ version(module="kannich-java") }}")
@file:DependsOn("dev.kannich:kannich-maven:{{ version(module="kannich-maven") }}")

import dev.kannich.java.Java
import dev.kannich.maven.Maven
import dev.kannich.stdlib.*
import dev.kannich.tools.*

pipeline {
    val java = Java("21")
    val maven = Maven("3.9.6", java)

    execution("build", "Compiles and packages the application") {
        job {
            val buildNumber = getEnv("BUILD_NUMBER") ?: "local"

            artifacts {
                includes("target/*.jar")
            }

            log("Build number: $buildNumber")
            maven.exec("clean", "package", "-DskipTests", "-Dbuild.number=$buildNumber")
        }
    }

    execution("test", "Runs the test suite") {
        job {
            artifacts(On.SUCCESS_OR_FAILURE) {
                includes("target/surefire-reports/**")
            }

            maven.exec("test")
        }
    }
}
```

Run the executions with `./kannichw build` and `./kannichw test`. Both work identically on any machine with Docker - your laptop, a teammate's machine, or a CI runner.
