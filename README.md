# Kannich

Kannich is a modern CI pipeline executor designed to eliminate "YAML and shell script hell" by providing a powerful, extensible KotlinScript-based DSL for defining CI workflows. It allows you to run your CI builds anywhere — from your local machine to any CI/CD service — with complete consistency.

## Key Concepts

### Local-First CI
Kannich enables you to run and debug your CI pipelines locally before pushing changes to a remote server. This significantly shortens the feedback loop for developers and allows for complex builds to be performed without having local toolchains installed, as long as a Docker daemon is available.

### Container-Native Isolation
All CI jobs are executed within Docker containers, ensuring a consistent and isolated build environment. Kannich uses an advanced overlay filesystem to isolate changes made during the build process from your local filesystem, keeping your host environment clean.

### Extensible DSL
Using Kotlin for pipeline definitions provides the full power of a programming language. You can easily reuse and share CI logic, define custom tools, and leverage the type-safety and IDE support that comes with Kotlin.

### Seamless Integration
Kannich is designed to complement existing CI/CD platforms like GitHub Actions, GitLab CI, or Jenkins, rather than replacing them. It reduces vendor lock-in by keeping the core build logic portable and independent of the CI runner's specific configuration format.

## Core Features

- **Kotlin-Based Pipelines**: Define jobs, dependencies, and complex workflows using a type-safe DSL.
- **Smart Tooling**: Automatically downloads, installs, and caches tools (like Java, Maven, Docker, etc.) on first use.
- **Parallel & Sequential Execution**: Fine-grained control over job orchestration and execution flow.
- **Artifact Management**: Declarative artifact collection that automatically syncs files from the build container back to your workspace.
- **Efficient Caching**: Supports caching of dependencies and build results to speed up subsequent executions.

## A Sample Pipeline

```kotlin
pipeline {
    val java = Java(version = "21")
    val maven = Maven(version = "3.9.6", java = java)

    val compile = job("Compile") {
        maven.run("clean compile")
    }

    val test = job("Unit Tests") {
        maven.run("test")
        artifacts {
            include("target/surefire-reports/**")
        }
    }

    execution("build-test") {
        sequentially {
            job(compile)
            job(test)
        }
    }
}
```
