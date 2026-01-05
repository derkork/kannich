@echo off
REM Kannich Wrapper Script
REM Runs Kannich inside Docker - no local JVM required.
REM Commit this file with your project to enable portable CI builds.

setlocal enabledelayedexpansion

REM Configuration
if "%KANNICH_IMAGE%"=="" set KANNICH_IMAGE=derkork/kannich:latest
if "%KANNICH_CACHE_DIR%"=="" set KANNICH_CACHE_DIR=%USERPROFILE%\.kannich\cache

REM Default prefixes for environment variables to pass to Docker
set DEFAULT_ENV_PREFIXES=CI_ GITHUB_ BUILD_ CIRCLE_ TRAVIS_ BITBUCKET_ KANNICH_

REM Determine project directory
set PROJECT_DIR=%~dp0
set PROJECT_DIR=%PROJECT_DIR:~0,-1%

REM Detect --dev-mode / -d flag (passed through to CLI, but we need to mount volume)
set DEV_MODE=0
for %%A in (%*) do (
    if "%%A"=="--dev-mode" set DEV_MODE=1
    if "%%A"=="-d" set DEV_MODE=1
)

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

REM Host .m2 repository path for dev mode
set HOST_M2_REPO=%USERPROFILE%\.m2\repository
set M2_DIR_DOCKER=%HOST_M2_REPO:\=/%
set M2_DRIVE_LETTER=%M2_DIR_DOCKER:~0,1%
set M2_PATH_REMAINDER=%M2_DIR_DOCKER:~2%
set M2_DOCKER_PATH=/%M2_DRIVE_LETTER%%M2_PATH_REMAINDER%

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

REM Always forward all DOCKER_* environment variables (regardless of .kannichenv)
for /f "tokens=1 delims==" %%V in ('set DOCKER_ 2^>nul') do (
    set "ENV_ARGS=!ENV_ARGS! -e %%V"
)

REM Determine Docker socket mount based on DOCKER_HOST
REM If DOCKER_HOST is set and is a unix socket, mount that path
REM If tcp:// or npipe://, no socket mount needed (env var handles it)
REM Otherwise default to /var/run/docker.sock
set DOCKER_SOCKET_MOUNT=-v //var/run/docker.sock:/var/run/docker.sock
if defined DOCKER_HOST (
    echo !DOCKER_HOST! | findstr /B /C:"unix://" >nul
    if !errorlevel!==0 (
        REM Extract socket path from unix:///path/to/socket
        set "DOCKER_SOCKET_PATH=!DOCKER_HOST:unix://=!"
        set "DOCKER_SOCKET_MOUNT=-v !DOCKER_SOCKET_PATH!:!DOCKER_SOCKET_PATH!"
    ) else (
        echo !DOCKER_HOST! | findstr /B /C:"tcp://" >nul
        if !errorlevel!==0 (
            REM TCP connection - no socket mount needed
            set "DOCKER_SOCKET_MOUNT="
        ) else (
            echo !DOCKER_HOST! | findstr /B /C:"npipe://" >nul
            if !errorlevel!==0 (
                REM Named pipe connection - no socket mount needed
                set "DOCKER_SOCKET_MOUNT="
            )
        )
    )
)

REM Mount DOCKER_CERT_PATH if set (for TLS certificate resolution)
REM Mount certs to Docker-formatted path and override env var so container can find them
set DOCKER_CERT_MOUNT=
set DOCKER_CERT_ENV=
if defined DOCKER_CERT_PATH (
    set "CERT_DIR_DOCKER=!DOCKER_CERT_PATH:\=/!"
    set "CERT_DRIVE_LETTER=!CERT_DIR_DOCKER:~0,1!"
    set "CERT_PATH_REMAINDER=!CERT_DIR_DOCKER:~2!"
    set "CERT_DOCKER_PATH=/!CERT_DRIVE_LETTER!!CERT_PATH_REMAINDER!"
    set "DOCKER_CERT_MOUNT=-v "!CERT_DOCKER_PATH!:!CERT_DOCKER_PATH!""
    set "DOCKER_CERT_ENV=-e DOCKER_CERT_PATH=!CERT_DOCKER_PATH!"
)

REM Dev mode: mount host .m2/repository
set DEV_MODE_MOUNT=
if %DEV_MODE%==1 (
    if not exist "%HOST_M2_REPO%\" (
        echo Error: Dev mode requires %%USERPROFILE%%\.m2\repository to exist.
        echo Run 'mvn install' on a project first to create it.
        exit /b 1
    )
    set "DEV_MODE_MOUNT=-v "%M2_DOCKER_PATH%:/kannich/dev-repo""
)

REM Run Kannich inside Docker
REM --init: Use tini for proper signal handling and zombie reaping
REM --name: Named container for potential cleanup
docker run --rm ^
    --init ^
    --name %CONTAINER_NAME% ^
    -v "%PROJECT_DOCKER_PATH%:/workspace" ^
    -v "%CACHE_DOCKER_PATH%:/kannich/cache" ^
    %DOCKER_SOCKET_MOUNT% ^
    %DOCKER_CERT_MOUNT% ^
    %DEV_MODE_MOUNT% ^
    %ENV_ARGS% ^
    %DOCKER_CERT_ENV% ^
    -w /workspace ^
    %KANNICH_IMAGE% ^
    /kannich/jdk/bin/java -jar /kannich/kannich-cli.jar %*

endlocal
