## Kannich

Kannich is a CI pipeline executor for running CI builds anywhere - on your local machine or in any CI/CD service. It is designed to avoid the YAML hell often associated with configuring CI pipelines. In Kannich you define your CI jobs using a KotlinScript-based DSL which provides you with the full power of a programming language to define your CI workflows. This DSL is extensible allowing you to reuse and share common CI logic across multiple projects. DSL files can be hosted in any HTTP accessible location, making it easy to centralize and manage your CI configurations. 

### Features

- Kannich can be bootstrapped locally using a kannichw wrapper script (similar to Gradle's gradlew or Maven's mvnw). 
- Kannich executes all CI jobs in a Docker container ensuring a consistent and isolated build environment.
- Kannich supports caching of dependencies and build artifacts to speed up subsequent builds.
- Kannich provides built-in support for common CI tasks such as checking out code from Git repositories, running tests, building artifacts, and publishing results.
- Kannich can be easily integrated with popular CI/CD services like GitHub Actions, GitLab CI, Jenkins, etc.
- Because you can run Kannich locally, you can test and debug your CI jobs without needing to push changes to a remote CI server all the time.

### A sample Kannich DSL file

```kotlin
// Add dependencies to use Kannich stdlib and optional plugins
@file:DependsOn("dev.kannich:kannich-stdlib:1.0.0")
@file:DependsOn("dev.kannich:kannich-jvm:1.0.0")
@file:DependsOn("dev.kannich:kannich-docker:1.0.0")

import dev.kannich.stdlib.*
import dev.kannich.jvm.*
import dev.kannich.docker.*
        
pipeline {
    // comes from the java plugin, will automatically download
    // and install java 11 and store the the download in the kannich cache
    // note, that installation is on first use, not on definition
    val java = Java(version = "11")
    // comes from the maven plugin, will automatically download
    // and install maven 3.6.3 and store the the download in the kannich cache
    val maven = Maven(version = "3.6.3", java = java)
 
    
    val compile = job("Compile") {
        // runs the maven command using the installed maven version
        maven.run("clean compile")        
    }
    
    val test = job("Unit Tests") {
        // runs the unit tests
        maven.run("test", "-Dmaven.test.type=unit")        
        
        // collects the surefire reports as build artifacts
        artifacts {
            include("target/surefire-reports/**")
        }
    }
    
    val integrationTest = job("Integration Tests") {
        // comes from the docker plugin, will automatically provide a docker daemon
        // with the given version
        val docker  = Docker(version = "1.42.0")
        
        // starts the docker compose services defined in the given file
        docker.compose {
            file = "src/test/resources/docker-compose.yml"
            command = "up"
        }
        
        // runs the integration tests
        maven.run("test", "-Dmaven.test.type=integration")        
        
        // stops the docker compose services
        docker.compose {
            file = "src/test/resources/docker-compose.yml"
            command = "down"
        }
        
        // collects the failsafe reports as build artifacts
        artifacts {
            include("target/failsafe-reports/**")
        }
    }
    
    
    val buildAndTest = execution("build-test") {
        // executions can group multiple jobs, they are executed in the given
        // order
        sequentially {
            // compile job will run before the tests
            job(compile)
            // unit tests and integration tests will run in parallel after compile
            parallel {
                job(test)
                job(integrationTest)
            }
        }
    }
    
    execution("deploy") {
        // executions can call other executions
        execution(buildAndTest)
        
        // jobs can also be defined inline inside of executions
        job("Deploy") {
            // deploys the built artifacts to a remote repository
            maven.run("deploy", "-DskipTests")
        }
    }
}
```

To run Kannich with the above DSL file, save it as `.kannichfile.main.kts` and execute the following command:

```bash
# this will run the deploy execution
kannichw deploy
```

This will execute the entire pipeline and produce the build artifacts. The artifacts will be put in your directory at the same path from which they were collected inside the build container. You can override this with the `--artifacts-dir` option (e.g `kannichw --artifacts-dir=./my-artifacts deploy`).

### Execution model

When you start Kannich, Kannich will create a Docker container and mount your project directory into this container. It will then use an overlay filesystem to isolate the changes made during the build process from your local filesystem. This ensures that your local environment remains clean and unaffected by the build process. Now Kannich will read the `.kannichfile.main.kts` and execute the specified execution. Each job will run in isolation inside the container and will get its own working directory. Subsequent jobs will see the changes made by previous jobs. Parallel jobs will see the changes of jobs running before them but not of other parallel jobs. For each job, Kannich will collect the artifacts. Once the execution is complete, Kannich will copy the collected build artifacts from the container back to your local filesystem.


### Goals and non-goals

The idea behind this is to provide a simple and flexible way to run CI scripts cross-platform, no matter which CI system you are using. It's intended to do away with the mixture of YAML scripting and shell scripts that seems to be everywhere, and that is really hard to maintain and also not very easy to share across projects. Another very big reason why this exists is to simplify the way in which build pipelines are set up. We want to be able to create a pipeline on the developer's machine and test it directly on the developer's machine without having to do a round trip over Git and the CI server. However, this does not mean that Kannich is supposed to replace traditional CI systems, so it will not replace GitHub Actions, GitLab, or whatever else is currently used. It is more like complementing these systems. Also, it reduces vendor lock-in with classical CI systems because most of the logic is done in a way that doesn't make it super hard to switch from GitLab to Jenkins or from Jenkins to GitHub Actions or basically any system. 


### Technical implementation

Kannich is implemented in Kotlin and is built with the Maven build system. If it is already installed on the user's machine, it can build itself. However, there's also a bootstrapping script which can bootstrap a Kannich installation from a Git checkout of Kannich.

---

## Contribution Guidelines

### Project Structure

```
kannich/
├── kannich-core/           # Core executor engine
│   └── src/main/kotlin/dev/kannich/core/
│       ├── docker/         # Docker client, container management, overlay FS
│       ├── dsl/            # Kotlin script host and DSL definitions
│       ├── execution/      # Job orchestration (sequential/parallel)
│       ├── cache/          # Tool and dependency caching
│       ├── artifact/       # Build artifact collection
│       └── plugin/         # Plugin system interfaces
├── kannich-stdlib/         # Standard library DSL (pipeline, job, execution)
├── kannich-cli/            # Command-line interface
├── kannich-jvm/            # JVM plugin (Java/Kotlin SDK management)
├── kannich-maven/          # Maven plugin
├── kannich-docker/         # Docker-in-Docker plugin
├── kannich-builder-image/  # Custom builder Docker image
├── kannichw                # End-user wrapper (Unix) - committed with user projects
├── kannichw.bat            # End-user wrapper (Windows)
├── bootstrap.sh            # Developer bootstrap script (Unix)
└── bootstrap.bat           # Developer bootstrap script (Windows)
```

### Code Style

**Comments: Explain why, not what**

The code itself shows what happens. Comments should explain the reasoning behind decisions.

```kotlin
// Good: explains the reasoning
// Overlay ensures parallel jobs don't see each other's changes
val overlay = createOverlay(baseLayer)

// Bad: describes what the code already shows
// Creates an overlay from the base layer
val overlay = createOverlay(baseLayer)
```

Keep comments concise - one line preferred, two maximum.

### Building Kannich

**For Kannich developers** (requires JVM and Maven locally):
```bash
./bootstrap.sh    # Unix
bootstrap.bat     # Windows
```

**For projects using Kannich** (requires only Docker):
```bash
./kannichw build  # runs inside Docker, no local JVM needed
```

### Key Technical Decisions

| Area | Choice | Why |
|------|--------|-----|
| Language | Kotlin 2.1.0 | Best scripting support, modern language features |
| Build | Maven | Simple bootstrapping, reliable Kotlin support |
| Docker API | 1.44+ | Modern overlay FS support, Docker 24+ |
| Job Isolation | overlay2 volumes | Native Docker driver, efficient, no privileged mode |
| DSL Engine | Custom script definition | Supports @file:DependsOn with Maven resolution |

### Dependencies

Core dependencies managed in parent POM:
- `org.jetbrains.kotlin:kotlin-scripting-*` - Script host and compilation
- `com.github.docker-java:docker-java-*` - Docker API client
- `org.slf4j:slf4j-api` - Logging facade