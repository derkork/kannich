+++
title = "Environment Variables"
weight = 2
+++

Kannich currently supports the following environment variables to control its behavior when kannich is invoked though the `kannichw` script.

| Variable                         | Default                            | Description                                                                                                                     |
|----------------------------------|------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `KANNICH_IMAGE`                  | `derkork/kannich:latest`           | The Docker image to use for the Kannich builder.                                                                                |
| `KANNICH_DOCKER_PROXY_PREFIX`    | _(none)_                           | If set, prepended to the image name. Useful for routing image pulls through a registry mirror or proxy.                         |
| `KANNICH_DOCKER_PROXY_URL`       | _(none)_                           | URL of a Docker registry proxy. When set together with credentials, Kannich will log in to this registry before pulling images. |
| `KANNICH_DOCKER_PROXY_USERNAME`  | _(none)_                           | Username for authenticating with the Docker registry proxy.                                                                     |
| `KANNICH_DOCKER_PROXY_PASSWORD`  | _(none)_                           | Password for authenticating with the Docker registry proxy.                                                                     |
| `KANNICH_CACHE_DIR`              | _(docker volume `kannich-cache`)_  | Host directory to use as the build cache. When unset, Kannich creates and uses a Docker volume named `kannich-cache`.           |
| `KANNICH_BOOTSTRAP_SETTINGS_XML` | contents of `~/.m2/settings.xml`   | Maven `settings.xml` content passed into the build. If not set, Kannich reads `~/.m2/settings.xml` automatically if it exists.  |
| `KANNICH_PROJECT_DIR`            | directory of the `kannichw` script | Root directory of the project to build. Mounted into the container as `/workspace`.                                             |
