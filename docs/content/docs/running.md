+++
title = "Running Kannich"
weight = 1
+++

## The Kannich wrapper scripts
Kannich comes with wrapper scripts for MacOS/Linux and Windows. See the [quick start](@/docs/quick-start.md) for how to get them. We recommend adding these scripts to your project and commiting them to your repository right next to the `.kannichfile.main.kts`, so all team members can run the same pipelines. They are also needed in some CI systems, so having them in the repository is a good idea.

## Running Locally
To run Kannich locally, use the wrapper script for your operating system. On Linux/MacOS: 

```bash
./kannichw <execution>
```

And on Windows:

```ps1
.\kannichw.ps1 <execution>
```

You need a Docker daemon running to run Kannich locally. The wrapper scripts will download the Kannich image, prepare a cache volume and then start kannich in a container. Your local checkout will be mounted into the container. Note that Kannich is built in a way that modifications that occur in the container will not be reflected on the host. So for example, if you happen to modify or delete some files of your checkout in the `.kannichfile.main.kts`, your files on the host will remain untouched. The only exception is if you use the `artifacts` functionality - all artifacts will be copied back to the host when Kannich exits. 

### The Cache
Kannich uses caching extensively to speed up the build process. By default, the Kannich wrapper will create a Docker volume named `kannich-cache` and mount it to the container. If the volume already exists, Kannich will reuse it. The cache is used to store downloads and dependencies. It is safe to delete the cache volume at any time if you want to free up space or if you want to start over. It is also possible to store the cache in a local directory by setting the `KANNICH_CACHE_DIR` environment variable. If this is set, the wrapper will use this directory rather than a Docker volume. However, using the volume has better performance and less chance to do _interesting_ things on Windows, so usually you will want to use the volume over the local folder.


## Running in CI
Running Kannich in CI is pretty much the same as running locally. You will need a Docker daemon running in your CI environment, and then you can run the wrapper script exactly like you can locally. This way you know that the build will work exactly the same way in CI as it does locally (at least for the parts that are not environment-specific). However, most CI systems have their own caching mechanism and so a bit of extra work is needed to integrate Kannich with them. 

### CI system flavors
There are two main flavors of CI systems - systems that run CI executions in containers (e.g. GitLab, CircleCI, Woodpecker CI) and systems that run CI executions in VMs (e.g. GitHub Actions, Bitbucket Pipelines, TravisCI). There are also systems that allow both (Jenkins, TeamCity). In general, you could use the wrapper scripts everywhere. However, on container native systems it can be worthwile using the Kannich image directly without going through the wrapper as this avoids firing up a second docker container to run the wrapper.

### GitHub Actions

The easiest way to run Kannich on GitHub Actions is to use the [kannich-action](https://github.com/derkork/kannich-action). It handles cache setup, permissions and running your executions in one step.

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      # always pin actions to a SHA hash to mitigate against supply chain attacks
      - uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4

      - uses: derkork/kannich-action@4d9359ec7acdedefb1a1742492673f1b7997dd5c # v1
        with:
          image: derkork/kannich@sha256:a23067a63c1943b4f24a96bf9841279c2895ce97cc7565133773f6814ae8281e # 0.11.0-0.10.0
          executions: |
            build
            test
```

You can pass additional environment variables to Kannich using the `env` input:

```yaml
      - uses: derkork/kannich-action@4d9359ec7acdedefb1a1742492673f1b7997dd5c # v1
        with:
          image: derkork/kannich@sha256:a23067a63c1943b4f24a96bf9841279c2895ce97cc7565133773f6814ae8281e # 0.11.0-0.10.0
          executions: build
          env: |
            SOME_VARIABLE=some-value
            SOME_SECRET=${{ secrets.MY_SECRET }}
```

See the [kannich-action README](https://github.com/derkork/kannich-action) for a full list of inputs.

### GitLab CI

Here is an example of how to run Kannich in GitLab CI:

```yaml
# Pulls the image. To minimize the attack surface of supply chain attacks, we recommend using the @sha256:... form of 
# the image reference. If you don't like that, we recommend using the tag form over "latest", so you know which version
# of the image you are using. We also recommend using the dependency proxy built into GitLab as this speeds up pulls 
# and prevents issues with Docker Hub rate limits.
image: 
  # Recommended way: use the dependency proxy + @sha256:...
  name: $CI_DEPENDENCY_PROXY_GROUP_IMAGE_PREFIX/derkork/kannich@sha256:a23067a63c1943b4f24a96bf9841279c2895ce97cc7565133773f6814ae8281e
  
  # You can also use a tag:
  # name: $CI_DEPENDENCY_PROXY_GROUP_IMAGE_PREFIX/derkork/kannich:0.11.0-0.10.0
  
  # If you don't want to use the dependency proxy, you can use this instead (works also with @sha256:...):
  # name: derkork/kannich:0.11.0-0.10.0
  
  # This disables Kannich's default entrypoint - it is necessary, so GitLab can do its own things before Kannich starts.
  entrypoint: [""]

# Cache setup. This allows us to make Kannich's cache available to all jobs, and also to reuse it between builds.
cache:
  - key: kannich
    paths:
      - .gitlab-cache  
      
# We need to set up a few variables, so Kannich integrates nicely with GitLab.
variables:
  # This tells Kannich, where Gitlab has checked out the project.
  KANNICH_PROJECT_DIR: $CI_PROJECT_DIR
  
  # This tells Kannich, where the cache should be stored. Note how it refers to the cache directory we created above.
  KANNICH_CACHE_DIR: $CI_PROJECT_DIR/.gitlab-cache
  
  # Fastzip speeds up GitLabs cache compression by a lot. It is strongly recommended for builds that use Kannich.
  FF_USE_FASTZIP: "true"
  CACHE_COMPRESSION_LEVEL: "fastest"

test:
  variables:
    # These will be available in Kannich using the `getEnv` function.
    SOME_VARIABLE: "some value"
  script:
    # This is the entrypoint that Kannich uses and we use it to start a particular execution.
    - /kannich/scripts/entrypoint.sh <execution>
```



