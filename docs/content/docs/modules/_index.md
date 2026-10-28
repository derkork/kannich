+++
title = "Modules"
weight = 3
sort_by = "weight"
template = "section.html"
+++

Kannich is built around a set of modules, each providing support for a specific tool or technology. You pull in exactly the modules you need by declaring them as dependencies at the top of your `.kannichfile.main.kts`.

Each module page covers what the module provides, how to add it to your pipeline, and any configuration options it supports.

## Module Versions

Current versions of all available Kannich modules and the Kannich Docker image. Follow the links in the last column to see what changed in each version.

| Module | Version | Description | Changelog |
|--------|---------|-------------|-----------|
| `derkork/kannich` | {{ version(module="kannich-builder-image") }} | Docker image that runs your pipelines | {{ changelog(module="kannich-builder-image", text="Changes") }} |
| `kannich-age` | {{ version(module="kannich-age") }} | age file encryption tool | {{ changelog(module="kannich-age", text="Changes") }} |
| `kannich-aws-cli` | {{ version(module="kannich-aws-cli") }} | AWS CLI tool support | {{ changelog(module="kannich-aws-cli", text="Changes") }} |
| `kannich-gcloud-cli` | {{ version(module="kannich-gcloud-cli") }} | Google Cloud CLI tool support | {{ changelog(module="kannich-gcloud-cli", text="Changes") }} |
| `kannich-ggg` | {{ version(module="kannich-ggg") }} | GGG Godot version and addon management | {{ changelog(module="kannich-ggg", text="Changes") }} |
| `kannich-helm` | {{ version(module="kannich-helm") }} | Helm chart management | {{ changelog(module="kannich-helm", text="Changes") }} |
| `kannich-java` | {{ version(module="kannich-java") }} | Java SDK management and execution | {{ changelog(module="kannich-java", text="Changes") }} |
| `kannich-maven` | {{ version(module="kannich-maven") }} | Maven build tool support | {{ changelog(module="kannich-maven", text="Changes") }} |
| `kannich-node` | {{ version(module="kannich-node") }} | Node.js runtime management | {{ changelog(module="kannich-node", text="Changes") }} |
| `kannich-pre-commit` | {{ version(module="kannich-pre-commit") }} | Pre-commit hooks framework | {{ changelog(module="kannich-pre-commit", text="Changes") }} |
| `kannich-pulumi` | {{ version(module="kannich-pulumi") }} | Pulumi infrastructure as code | {{ changelog(module="kannich-pulumi", text="Changes") }} |
| `kannich-quarkus` | {{ version(module="kannich-quarkus") }} | Quarkus framework support | {{ changelog(module="kannich-quarkus", text="Changes") }} |
| `kannich-sops` | {{ version(module="kannich-sops") }} | SOPS secrets encryption tool | {{ changelog(module="kannich-sops", text="Changes") }} |
| `kannich-stdlib` | {{ version(module="kannich-stdlib") }} | Core standard library with pipeline DSL | {{ changelog(module="kannich-stdlib", text="Changes") }} |
| `kannich-terraform` | {{ version(module="kannich-terraform") }} | Terraform infrastructure management | {{ changelog(module="kannich-terraform", text="Changes") }} |
| `kannich-test` | {{ version(module="kannich-test") }} | Testing utilities and helpers | {{ changelog(module="kannich-test", text="Changes") }} |
| `kannich-tools` | {{ version(module="kannich-tools") }} | Base classes for tool plugins | {{ changelog(module="kannich-tools", text="Changes") }} |
| `kannich-trivy` | {{ version(module="kannich-trivy") }} | Trivy security scanner integration | {{ changelog(module="kannich-trivy", text="Changes") }} |
| `kannich-uv` | {{ version(module="kannich-uv") }} | UV Python package manager | {{ changelog(module="kannich-uv", text="Changes") }} |
