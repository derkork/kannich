# Kannich CI Integration Improvements

## Background

CI systems fall into two camps based on whether job steps run on a VM (Docker available
natively) or inside containers (Docker not available without extra setup).

### VM-based runners — `./kannichw` works as-is

These systems provide a virtual machine with Docker pre-installed. `./kannichw` works exactly
as it does locally — no extra configuration needed.

| System | Notes |
|---|---|
| **GitHub Actions** | Docker on all standard runners. Already works, demonstrated by the smoke-test workflow. |
| **Azure Pipelines** | Microsoft-hosted agents include Docker. Same story as GitHub Actions. |
| **Travis CI** | Docker available in Ubuntu environments by default. |
| **AWS CodeBuild** | Standard build environments include Docker. |
| **Jenkins** | Works on bare-metal/VM agents with Docker installed. Docker-based agents have the container-native problem (see below). |
| **Buildkite** | Self-hosted agents — works wherever Docker is installed on the host. |

### Container-native CI — the problem

These systems run each job step inside a Docker container. Docker is not available inside that
container without explicit DinD setup, which means running `./kannichw` requires pulling
`docker:dind` as a service *and* then pulling the Kannich image — two downloads per job:

```
CI runner → docker:dind (download) → ./kannichw → derkork/kannich (download) → runs pipeline
```

| System | Notes |
|---|---|
| **GitLab CI** | Default runner mode. Requires `docker:dind` service to use `./kannichw`. |
| **Bitbucket Pipelines** | Every step runs in a specified image. Same double-pull problem as GitLab. |
| **Drone CI / Woodpecker CI** | Every step is a container. Same problem. |
| **Google Cloud Build** | Every step is a container. Has no DinD service concept — the native image approach below is the *only* viable path. |
| **CircleCI** | `machine` executor (VM) works fine; `docker` executor has the same container-native problem. |
| **Jenkins Docker agents** | When the Docker plugin runs builds inside containers, same issue as GitLab. |

### The solution for container-native CI

Use the Kannich image itself as the job image. The CI system checks out the repository into the
running container and executes pipeline steps directly — no `docker:dind`, no second image pull:

```
CI runner (privileged) → derkork/kannich (download, job image) → runs pipeline
```

`Docker.enable()` continues to work because it starts its own dockerd via supervisord inside
the Kannich container. This is already DinD; it just runs inside the Kannich image rather than
being set up externally. Requires `privileged: true` on the runner, which is already needed by
any pipeline that calls `Docker.enable()`.

### Privileged mode is a hard requirement

Kannich requires `CAP_SYS_ADMIN` for two independent reasons:

- **overlayfs** — used for job isolation (creating a fresh layer per job)
- **dockerd** — required if the pipeline calls `Docker.enable()`

A copy-based fallback for overlayfs would not help: any pipeline that needs Docker still
requires privileged mode, and pipelines that don't need Docker are typically running on
VM-based CI where privileged is implicit anyway. The fallback would only benefit a narrow
slice (container-native CI, no Docker, non-privileged runner) that is unlikely in practice.

Every container-native CI system does provide a way to enable privileged mode, but availability
varies between self-hosted and cloud/SaaS runners:

| System | Privileged on cloud/SaaS | Privileged on self-hosted |
|---|---|---|
| **GitLab CI** | Yes — GitLab.com shared Linux runners run in privileged mode | Yes — set `privileged = true` in runner `config.toml` |
| **Bitbucket Pipelines** | Yes — `privileged: true` at step level | Yes |
| **Drone / Woodpecker** | Restricted (Drone Cloud largely defunct) | Yes — `privileged: true` at step level |
| **Google Cloud Build** | Needs verification — runs as root, Docker works, full `CAP_SYS_ADMIN` unclear | Yes (self-hosted workers) |
| **CircleCI** | No — not available on `docker` executor; use `machine` executor instead | Yes — `machine` executor on self-hosted |
| **Jenkins Docker agents** | N/A (always self-hosted) | Yes — pass `--privileged` in agent spec |

**Bottom line:** on self-hosted runners, privileged mode is always achievable. On SaaS,
both GitLab.com and Bitbucket support it on their shared runners. CircleCI cloud docker executor
is the main exception — use their `machine` executor instead.

---

## Issues to Fix

### 1. `.kannich_current_env` file never written (Breaking)

`kannichw` writes all host environment variables to `$PROJECT_DIR/.kannich_current_env` using
null-byte delimiters before starting the container. `Main.kt:determineHostEnvVars()` reads this
file from `/workspace/.kannich_current_env` to inject host env into the pipeline.

When Kannich is invoked directly as the job image, this file is never created. As a result,
**all CI environment variables (`CI_*`, `KANNICH_*`, secrets, tokens, pipeline metadata) are
invisible to the pipeline.**

**Fix:** When `.kannich_current_env` is absent, fall back to reading `System.getenv()` directly.
Container-native CI systems inject variables directly into the container's process environment,
so they will already be present. The existing prefix filtering (`.kannichenv` / default prefix
list) should still apply to this fallback path.

---

### 2. `/workspace` hardcoded throughout (Breaking)

`Kannich.kt` defines `WORKSPACE_DIR = "/workspace"` as a compile-time constant used in:

- `LayerManager.kt:38` — lower dir for overlayfs (source of truth for job isolation)
- `Main.kt:103` — artifacts output directory
- `Main.kt:135` — path to `.kannich_current_env`
- `Main.kt:160` — path to `.kannichenv`

Container-native CI systems check out to their own paths (e.g. GitLab uses `CI_PROJECT_DIR`
like `/builds/group/project`, Bitbucket uses `/opt/atlassian/pipelines/agent/build`, Cloud Build
uses `/workspace` — coincidentally correct — but this varies). If the path is wrong, the
overlayfs lower dir points at an empty directory and jobs see no source files.

**Fix (two complementary approaches):**

- **Short-term:** Document per-system the variable to set to force checkout to `/workspace`
  (e.g. `GIT_CLONE_PATH: /workspace` for GitLab). Zero code changes required.
- **Long-term:** Make `WORKSPACE_DIR` configurable via a `KANNICH_WORKSPACE` environment
  variable, falling back to `/workspace`. This is the clean solution and removes the
  per-system workaround from user docs.

---

### 3. Cache directory not automatically mounted (Minor)

`kannichw` creates a Docker volume `kannich-cache` (or uses `KANNICH_CACHE_DIR`) and mounts it
to `/kannich/cache`. When running as a job image, no mount happens — `/kannich/cache` exists in
the image but is ephemeral.

**Fix:** Document that users set `KANNICH_CACHE_DIR` to a path managed by their CI system's
cache mechanism. No code change required. Example for GitLab:

```yaml
variables:
  KANNICH_CACHE_DIR: $CI_PROJECT_DIR/.kannich-cache
cache:
  key: kannich-$CI_COMMIT_REF_SLUG
  paths:
    - .kannich-cache/
```

---

### 4. Entrypoint is ignored by container-native CI (UX)

Container-native CI systems override `ENTRYPOINT` to inject their own shell for checkout and
script execution. The `ENTRYPOINT ["/kannich/scripts/entrypoint.sh"]` in the Dockerfile is not
used. Users must invoke the CLI explicitly in their `script:` block.

**Fix:** Add a `kannich` symlink on `PATH` in the image so users write `kannich some_job`
rather than `java -jar /kannich/kannich-cli.jar some_job`. Also improves the local
`docker run` experience.

```dockerfile
RUN ln -s /kannich/scripts/entrypoint.sh /usr/local/bin/kannich
```

---

## Example Configurations (after fixes)

### GitLab CI

```yaml
image: derkork/kannich@sha256:<pinned-digest>

variables:
  GIT_CLONE_PATH: /workspace          # until KANNICH_WORKSPACE is implemented
  KANNICH_CACHE_DIR: $CI_PROJECT_DIR/.kannich-cache

cache:
  key: kannich-$CI_COMMIT_REF_SLUG
  paths:
    - .kannich-cache/

some_job:
  script: kannich some_job
```

### Bitbucket Pipelines

```yaml
image: derkork/kannich@sha256:<pinned-digest>

pipelines:
  default:
    - step:
        name: some_job
        script:
          - kannich some_job
        caches:
          - kannich-cache

definitions:
  caches:
    kannich-cache: /workspace/.kannich-cache
```

Note: Bitbucket checks out to `/opt/atlassian/pipelines/agent/build` by default;
set `KANNICH_WORKSPACE` (once implemented) or use `GIT_CLONE_PATH` equivalent.

### Google Cloud Build

```yaml
steps:
  - name: derkork/kannich@sha256:<pinned-digest>
    args: [some_job]
    env:
      - KANNICH_CACHE_DIR=/workspace/.kannich-cache
```

Cloud Build checks out to `/workspace` by default — the only system where the hardcoded
path works without any workaround.

### Drone CI / Woodpecker CI

```yaml
steps:
  - name: some_job
    image: derkork/kannich@sha256:<pinned-digest>
    privileged: true
    environment:
      KANNICH_CACHE_DIR: /drone/src/.kannich-cache
    commands:
      - kannich some_job
```
