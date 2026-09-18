+++
title = "SOPS"
weight = 14
+++

## Overview

The SOPS module provides [Mozilla SOPS](https://github.com/getsops/sops) in your Kannich pipeline. It downloads the requested SOPS version automatically, caches it, and lets you encrypt and decrypt files backed by age, PGP, AWS KMS, GCP KMS, Azure Key Vault or HashiCorp Vault. Both `amd64` and `aarch64` architectures are supported.

Add the module to your `.kannichfile.main.kts`:

```kotlin
@file:DependsOn("dev.kannich:kannich-sops:{{ version(module="kannich-sops") }}")
```

You can always find the latest version on the [Module Versions](@/docs/modules/_index.md) page.

## Basic Usage

Instantiate `Sops` with the version you want, then call `exec()` with any arguments you'd pass to the `sops` command.

A common use case is keeping a secret that every developer needs for local pipeline execution - e.g. a `.connectivity.env` file with credentials for a shared dev environment - encrypted in the repository as `.connectivity.env.enc`, backed by age. Access is controlled by a list of age public keys kept as a `val` at the top of `.kannichfile.main.kts`: anyone already on the list can add a new developer's key and re-run `update-recipients` to grant them access. It is important that `.connectivity.env` is git-ignored, as only the encrypted file `.connectivity.env.enc` should ever be committed to git.

```kotlin
import dev.kannich.sops.Sops
import dev.kannich.stdlib.*
import dev.kannich.tools.*

// Age public keys allowed to decrypt .connectivity.env.enc. To add a new
// developer, have an existing recipient append their public key here and run
// the `update-recipients` execution below.
val ageRecipients = listOf(
    "age1ql3z7hjy54pw3hyww5ayyfg7zqgvc7w3j2elw8zmrj2kg5sfn9aqmcac8p",
    "age1n38wjzy8vsp0hy3vnwrvcvs2s0hs3rzcmch9egg2n7uh9k9rz9as8trvpk",
)

pipeline {
    val sops = Sops("3.13.3")

    execution("encrypt-connectivity-env", 
              "Encrypts .connectivity.env with sops and age. You'd use this on the initial setup " + 
              "and when changing .connectivity.env") {
        job {
            sops.exec("encrypt", "--age", ageRecipients.joinToString(","),
                "./.connectivity.env", "--output", "./.connectivity.env.enc")

            artifacts {
                includes(".connectivity.env.enc")
            }
        }
    }

    execution("decrypt-connectivity-env", "Decrypts .connectivity.env.enc for local pipeline execution") {
        job {
            // Each developer's own age secret key - never committed, only ever
            // held locally or injected by whatever runs the pipeline for them.
            requireEnv("SOPS_AGE_KEY")

            sops.exec("decrypt", "./.connectivity.env.enc", "--output", "./.connectivity.env")

            artifacts {
                includes(".connectivity.env")
            }
        }
    }

    execution("update-recipients", "Updates the recipients for .connectivity.env.enc. Can only be run if you are already a recipient.") {
        job {
            // sops needs to decrypt the existing data key before it can
            // re-wrap it for the (possibly extended) recipient list, so this
            // still requires a secret key that's already on ageRecipients.
            requireEnv("SOPS_AGE_KEY")

            sops.exec("updatekeys", "--yes", "--age", ageRecipients.joinToString(","),
                "./.connectivity.env.enc")

            artifacts {
                includes(".connectivity.env.enc")
            }
        }
    }
}
```

SOPS picks up decryption credentials from the standard environment variables of the backend you use (e.g. `SOPS_AGE_KEY` or `AGE_KEY_FILE` for age, `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` for AWS KMS, or `GOOGLE_APPLICATION_CREDENTIALS` for GCP KMS). In a CI environment you typically inject these as secrets before running SOPS commands.
