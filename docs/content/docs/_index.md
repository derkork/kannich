
+++
title = "Documentation"
sort_by = "weight"
template = "section.html"
+++

Tired of wrestling with cryptic YAML files and fragile shell scripts just to get your CI builds working? Yeah, we've all been there. That's why Kannich exists.

## What is Kannich?

Kannich is a CI pipeline executor that lets you write your build pipelines in **actual code** (KotlinScript, to be specific) instead of YAML. Then you can run these pipelines **anywhere**: your laptop, your teammate's machine, GitHub Actions, GitLab CI, Jenkins, wherever. Same pipeline, same results, every time.

Think of it as "what if your CI pipeline was just... code?" No more wondering if that cryptic shell one-liner will work on CI. If it works on your machine, it works everywhere.

## Why Should You Care?

**Local-First Development**: Run and debug your CI builds locally before pushing. No more "commit, push, wait 5 minutes, fail, repeat" cycles. You edit, you run it locally, and you see the results instantly.

**No Toolchain Hell**: Don't have Maven installed? Or that specific version of Node.js? Or Terraform? AWS tools? No problem. Kannich downloads and caches everything it needs automatically. All you need is Docker.

**Clean & Isolated**: Every job runs in a fresh Docker container with an isolated filesystem. Your build can install whatever it wants without polluting your actual machine.

**Type-Safe Pipelines**: Kotlin gives you autocomplete, refactoring, IDE support, and compile-time checks. No more typos in your YAML causing builds to fail at runtime. If you have a typo the editor will show you. If you want rename a thing, create a helper function, or anything really: it's all just code.

**Portable**: Your core build logic is just Kotlin code. Want to switch from GitHub Actions to GitLab CI? You just modify the small glue layer that connects Kannich to your CI system. No need to rewrite your entire pipeline because the syntax is different.

## How Does It Work?

1. **You write a `.kannichfile.main.kts`** in your project root. This is your pipeline definition, written in Kotlin.

2. **You run it with the wrapper**: `./kannichw` (or `kannichw.ps1` on Windows). The wrapper launches a Docker container with Kannich inside.

3. **Kannich executes your pipeline**: It reads your Kotlin script, downloads any tools you need (Maven, Node, whatever), and runs your build.

4. **You get results**: Artifacts, logs, test reports, everything you'd expect from a CI system, but running on your local machine.

Want to run it in CI? Just call the wrapper the same way. GitHub Actions, GitLab CI, Jenkins - they all can run `./kannichw`. Your pipeline code stays the same.

## Show Me the Code

Here's what a simple pipeline looks like:

```kotlin
pipeline {
    val java = Java(version = "21")
    val maven = Maven(version = "3.9.6", java = java)

    execution("build-test") {
        job {
            maven.exec("clean", "test")
            artifacts {
                include("target/surefire-reports/**")
            }
        }
    }
}
```

That's it. Straightforward Kotlin code that defines the tools you need, runs them and collects the artifacts.

## What's Next?

Ready to give it a try? Check out the [Quick Start](quick-start/) guide to create your first pipeline in about 5 minutes.

