+++
title = "Kannich Workflow"
weight = 3
+++


This page describes what happens, when you run a Kannich workflow through `kannichw`.

- The wrapper script verifies that the docker daemon is running.
- If `KANNICH_DOCKER_PROXY_URL` and `KANNICH_DOCKER_PROXY_USERNAME` and `KANNICH_DOCKER_PROXY_PASSWORD` are set, the wrapper script logs in to the docker proxy using `docker login`.
- The wrapper script checks if `KANNICH_CACHE_DIR` is set.
  - If it is set but doesn't exist, it is created.
  - If it is not set, the wrapper script checks if a docker volume named `kannich-cache` exists.
    - If it does not exist, the wrapper script creates a docker volume named `kannich-cache`.
- The wrapper script pulls the docker image given by `KANNICH_IMAGE`.
  - If no docker image is given, the wrapper script uses the default image `derkork/kannich:latest`.
- If `KANNICH_BOOTSTRAP_SETTINGS_XML` is not set, the wrapper script tries to loadd the user's Maven `setting.xml` file into `KANNICH_BOOTSTRAP_SETTINGS_XML`, if the file exists.
- The wrapper script reads all environment variables into a file `.kannich_current_env`. Variables are written as key-value pairs, `name=value` seperated by a zero byte.
- The wrapper script runs the docker container.
  - The directory where the wrapper script is located is mounted into the container at `/workspace`. If `KANNICH_PROJECT_DIR` is set, the directory indicated by `KANNICH_PROJECT_DIR` is mounted into the container at `/workspace`.
  - If the `-d` flag is set (dev mode), the user's local maven repository is mounted into the container at `/kannich/dev-repo`.
  - If `KANNICH_CACHE_DIR` is set, the directory indicated by `KANNICH_CACHE_DIR` is mounted into the container at `/kannich/cache`. Otherwise, the docker volume `kannich-cache` is mounted into the container at `/kannich/cache`.
- Docker runs the Kannich CLI as entrypoint.
- The Kannich CLI verifies that `.kannichfile.main.kts` exists and exits if not.
- The Kannich CLI builds the execution environment by reading `.kannich_current_env` and filtering out variables that are not present in `kannichenv`. If `kannichenv` does not exist only variables that start with `KANNICH_`, `CI_`, `GITHUB_`, `BUILD_`, `CIRCLE_`, `TRAVIS_` or `BITBUCKET_` are used.
- The Kannich CLI deletes the `.kannich_current_env` file.
- The Kannich CLI sets up the Maven environment. 
  - If `KANNICH_BOOTSTRAP_SETTINGS_XML` is set, its contents will be used as Maven `settings.xml`. Otherwise, the default Maven settings are used.
  - If started in dev mode, the user's local Maven repository that was mounted before is used as Maven repository.
- The Kannich CLI reads `.kannichfile.main.kts` and resolves external dependencies via Maven. 
- The Kannich CLI compiles and executes `.kannichfile.main.kts`.
- Depending on the given command line arguments, the requested tasks are executed.
