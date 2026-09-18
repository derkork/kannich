+++
title = "Age"
weight = 13
+++

## Overview

The Age module provides the [age](https://github.com/FiloSottile/age) file encryption tool in your Kannich pipeline. It downloads the requested age version automatically, caches it, and lets you encrypt and decrypt files as well as generate age key pairs with `age-keygen`. Both `amd64` and `aarch64` architectures are supported.

Add the module to your `.kannichfile.main.kts`:

```kotlin
@file:DependsOn("dev.kannich:kannich-age:{{ version(module="kannich-age") }}")
```

You can always find the latest version on the [Module Versions](@/docs/modules/_index.md) page.

## Basic Usage

Instantiate `Age` with the version you want, then call `exec()` with any arguments you'd pass to the `age` command. Use the `keygen` property to run `age-keygen`.

A typical setup splits key generation, encryption and decryption into separate executions: someone without `age` installed locally can run `generate-key` to get an identity, encryption only ever needs public recipient keys, and decryption only ever needs the secret key - so neither of the latter two executions requires the other's key material to be present.

```kotlin
import dev.kannich.age.Age
import dev.kannich.stdlib.*
import dev.kannich.tools.*
import java.io.File

pipeline {
    val age = Age("1.3.2")

    // Run this once to create a new identity. `key.txt` contains the secret key
    // and, as a comment on its first line, the matching public key (the
    // recipient). Publish it as an artifact so it can be downloaded and stored
    // somewhere safe, e.g. a secrets manager - it is not read by this execution
    // again, and it is the only place the secret key exists.
    execution("generate-key", "Generates a new age key pair") {
        job {
            artifacts { includes("key.txt") }

            age.keygen.exec("-o", "key.txt")
        }
    }

    // Encrypts a file for one or more recipients. Recipients are the public
    // keys printed by `generate-key`, passed in as a comma-separated list via
    // the RECIPIENTS environment variable so this execution never needs access
    // to any secret key.
    execution("encrypt", "Encrypts a secrets file for a list of recipients") {
        job {
            artifacts { includes("secret.txt.age") }

            val recipientArgs = requireEnv("RECIPIENTS").split(",")
                .flatMap { listOf("-r", it.trim()) }

            age.exec(*recipientArgs.toTypedArray(), "-o", "secret.txt.age", "secret.txt")
        }
    }

    // Decrypts a file using the secret key from the AGE_SECRET_KEY environment
    // variable (e.g. sourced from the secrets manager key.txt was stored in).
    // The key is written to a file for the duration of the job only, since
    // `age -i` expects a path rather than the key value itself.
    execution("decrypt", "Decrypts a secrets file using a secret key") {
        job {
            artifacts { includes("secret.txt") }

            val keyFile = File("key.txt")
            keyFile.writeText(requireEnv("AGE_SECRET_KEY"))

            age.exec("-d", "-i", keyFile.path, "-o", "secret.txt", "secret.txt.age")
        }
    }
}
```
