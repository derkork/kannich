@echo off
REM Kannich Wrapper Script
REM Runs Kannich inside Docker - no local JVM required.
REM Commit this file with your project to enable portable CI builds.

setlocal enabledelayedexpansion

REM Configuration
if "%KANNICH_VERSION%"=="" set KANNICH_VERSION=latest
set KANNICH_IMAGE=kannich/builder:%KANNICH_VERSION%
if "%KANNICH_CACHE_DIR%"=="" set KANNICH_CACHE_DIR=%USERPROFILE%\.kannich\cache

REM Determine project directory
set PROJECT_DIR=%~dp0
set PROJECT_DIR=%PROJECT_DIR:~0,-1%

REM Check for Docker
where docker >nul 2>&1
if errorlevel 1 (
    echo Error: Docker not found. Please install Docker.
    exit /b 1
)

docker info >nul 2>&1
if errorlevel 1 (
    echo Error: Docker daemon not running. Please start Docker.
    exit /b 1
)

REM Ensure cache directory exists on host (required for Docker mount)
if not exist "%KANNICH_CACHE_DIR%" (
    echo Creating cache directory: %KANNICH_CACHE_DIR%
    mkdir "%KANNICH_CACHE_DIR%"
    if errorlevel 1 (
        echo Error: Failed to create cache directory: %KANNICH_CACHE_DIR%
        exit /b 1
    )
)

REM Verify cache directory is accessible
if not exist "%KANNICH_CACHE_DIR%\" (
    echo Error: Cache directory not accessible: %KANNICH_CACHE_DIR%
    exit /b 1
)

REM Pull image if not present
docker image inspect %KANNICH_IMAGE% >nul 2>&1
if errorlevel 1 (
    echo Pulling Kannich builder image...
    docker pull %KANNICH_IMAGE%
)

REM Convert Windows paths to Docker-compatible format (D:\foo -> /d/foo)
set PROJECT_DIR_DOCKER=%PROJECT_DIR:\=/%
set CACHE_DIR_DOCKER=%KANNICH_CACHE_DIR:\=/%

REM Extract drive letter and convert to Docker path format
set DRIVE_LETTER=%PROJECT_DIR_DOCKER:~0,1%
set PROJECT_PATH_REMAINDER=%PROJECT_DIR_DOCKER:~2%
set PROJECT_DOCKER_PATH=/%DRIVE_LETTER%%PROJECT_PATH_REMAINDER%

set CACHE_DRIVE_LETTER=%CACHE_DIR_DOCKER:~0,1%
set CACHE_PATH_REMAINDER=%CACHE_DIR_DOCKER:~2%
set CACHE_DOCKER_PATH=/%CACHE_DRIVE_LETTER%%CACHE_PATH_REMAINDER%

REM Generate unique container name using timestamp
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set DATETIME=%%I
set CONTAINER_NAME=kannich-%DATETIME:~0,14%

REM Run Kannich inside Docker
REM --init: Use tini for proper signal handling and zombie reaping
REM --name: Named container for potential cleanup
REM Pass host paths as environment variables for nested container mounts
docker run --rm -it ^
    --init ^
    --name %CONTAINER_NAME% ^
    -v "%PROJECT_DOCKER_PATH%:/workspace" ^
    -v "%CACHE_DOCKER_PATH%:/kannich/cache" ^
    -v //var/run/docker.sock:/var/run/docker.sock ^
    -e "KANNICH_HOST_PROJECT_DIR=%PROJECT_DOCKER_PATH%" ^
    -e "KANNICH_HOST_CACHE_DIR=%CACHE_DOCKER_PATH%" ^
    -w /workspace ^
    %KANNICH_IMAGE% ^
    /kannich/jdk/bin/java -jar /kannich/kannich-cli.jar %*

endlocal
