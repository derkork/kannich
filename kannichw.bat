@echo off
REM Kannich Wrapper Script
REM Runs Kannich inside Docker - no local JVM required.
REM Commit this file with your project to enable portable CI builds.

setlocal enabledelayedexpansion

REM Configuration
if "%KANNICH_VERSION%"=="" set KANNICH_VERSION=latest
set KANNICH_IMAGE=kannich/builder:%KANNICH_VERSION%
if "%KANNICH_CACHE_DIR%"=="" set KANNICH_CACHE_DIR=%USERPROFILE%\.kannich\cache

REM Default prefixes for environment variables to pass to Docker
set DEFAULT_ENV_PREFIXES=CI_ GITHUB_ BUILD_ CIRCLE_ TRAVIS_ BITBUCKET_

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

REM Read environment variable prefixes from .kannichenv or use defaults
set ENV_PREFIXES=
if exist "%PROJECT_DIR%\.kannichenv" (
    for /f "usebackq eol=# tokens=*" %%P in ("%PROJECT_DIR%\.kannichenv") do (
        set "LINE=%%P"
        if defined LINE (
            set "ENV_PREFIXES=!ENV_PREFIXES! %%P"
        )
    )
) else (
    set ENV_PREFIXES=%DEFAULT_ENV_PREFIXES%
)

REM Build environment variable arguments for docker
set ENV_ARGS=
for %%P in (%ENV_PREFIXES%) do (
    for /f "tokens=1 delims==" %%V in ('set 2^>nul') do (
        set "VARNAME=%%V"
        set "PREFIX=%%P"
        REM Check if variable name starts with prefix
        if "!VARNAME:~0,3!"=="!PREFIX:~0,3!" (
            call :check_prefix "%%V" "%%P"
        )
    )
)
goto :after_check_prefix

:check_prefix
set "VARNAME=%~1"
set "PREFIX=%~2"
set "PREFIX_LEN=0"
set "TEMP_PREFIX=%PREFIX%"
:count_prefix_len
if defined TEMP_PREFIX (
    set "TEMP_PREFIX=!TEMP_PREFIX:~1!"
    set /a PREFIX_LEN+=1
    goto :count_prefix_len
)
set "VAR_PREFIX=!VARNAME:~0,%PREFIX_LEN%!"
if "!VAR_PREFIX!"=="!PREFIX!" (
    set "ENV_ARGS=!ENV_ARGS! -e !VARNAME!"
)
goto :eof

:after_check_prefix

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
    %ENV_ARGS% ^
    -w /workspace ^
    %KANNICH_IMAGE% ^
    /kannich/jdk/bin/java -jar /kannich/kannich-cli.jar %*

endlocal
