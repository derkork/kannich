@echo off
REM Kannich Wrapper Script
REM Runs Kannich inside Docker - no local JVM required.
REM Commit this file with your project to enable portable CI builds.

setlocal enabledelayedexpansion

REM Configuration
if "%KANNICH_IMAGE%"=="" set KANNICH_IMAGE=derkork/kannich:latest
if "%KANNICH_CACHE_DIR%"=="" set KANNICH_CACHE_DIR=%USERPROFILE%\.kannich\cache
set DEFAULT_ENV_PREFIXES=CI_ GITHUB_ BUILD_ CIRCLE_ TRAVIS_ BITBUCKET_ KANNICH_

REM Determine project directory
set PROJECT_DIR=%~dp0
set PROJECT_DIR=%PROJECT_DIR:~0,-1%

REM Detect --dev-mode / -d flag
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

REM Ensure cache directory exists
if not exist "%KANNICH_CACHE_DIR%" (
    echo Creating cache directory: %KANNICH_CACHE_DIR%
    mkdir "%KANNICH_CACHE_DIR%"
    if errorlevel 1 (
        echo Error: Failed to create cache directory: %KANNICH_CACHE_DIR%
        exit /b 1
    )
)

REM Pull image if not present
docker image inspect %KANNICH_IMAGE% >nul 2>&1
if errorlevel 1 (
    echo Pulling Kannich builder image...
    docker pull %KANNICH_IMAGE%
)

REM Convert paths to Docker format (D:\foo -> /d/foo)
call :to_docker_path "%PROJECT_DIR%" PROJECT_DOCKER_PATH
call :to_docker_path "%KANNICH_CACHE_DIR%" CACHE_DOCKER_PATH

REM Generate unique container name
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set DATETIME=%%I
set CONTAINER_NAME=kannich-%DATETIME:~0,14%

REM Read environment variable prefixes from .kannichenv or use defaults
set ENV_PREFIXES=
if exist "%PROJECT_DIR%\.kannichenv" (
    for /f "usebackq eol=# tokens=*" %%P in ("%PROJECT_DIR%\.kannichenv") do (
        set "LINE=%%P"
        if defined LINE set "ENV_PREFIXES=!ENV_PREFIXES! %%P"
    )
) else (
    set ENV_PREFIXES=%DEFAULT_ENV_PREFIXES%
)

REM Build environment variable arguments for docker
REM Iterate all env vars and check if they start with any prefix
set ENV_ARGS=
for /f "tokens=1 delims==" %%V in ('set 2^>nul') do (
    for %%P in (%ENV_PREFIXES%) do (
        set "_VAR=%%V"
        set "_PRE=%%P"
        call :check_prefix "%%V" "%%P"
    )
)
goto :after_prefix_check

:check_prefix
set "_VARNAME=%~1"
set "_PREFIX=%~2"
set "_LEN=0"
set "_TMP=%_PREFIX%"
:count_len
if defined _TMP (
    set "_TMP=!_TMP:~1!"
    set /a _LEN+=1
    goto :count_len
)
call set "_VAR_PRE=%%_VARNAME:~0,!_LEN!%%"
if "!_VAR_PRE!"=="!_PREFIX!" (
    set "ENV_ARGS=!ENV_ARGS! -e !_VARNAME!"
)
goto :eof

:after_prefix_check

REM Always forward all DOCKER_* environment variables
for /f "tokens=1 delims==" %%V in ('set DOCKER_ 2^>nul') do (
    set "ENV_ARGS=!ENV_ARGS! -e %%V"
)

REM Determine Docker socket mount
set DOCKER_SOCKET_MOUNT=-v //var/run/docker.sock:/var/run/docker.sock
if defined DOCKER_HOST (
    echo !DOCKER_HOST! | findstr /B /C:"unix://" >nul
    if !errorlevel!==0 (
        set "DOCKER_SOCKET_PATH=!DOCKER_HOST:unix://=!"
        set "DOCKER_SOCKET_MOUNT=-v !DOCKER_SOCKET_PATH!:!DOCKER_SOCKET_PATH!"
    ) else (
        echo !DOCKER_HOST! | findstr /B /C:"tcp://" >nul
        if !errorlevel!==0 (
            set "DOCKER_SOCKET_MOUNT="
        ) else (
            echo !DOCKER_HOST! | findstr /B /C:"npipe://" >nul
            if !errorlevel!==0 set "DOCKER_SOCKET_MOUNT="
        )
    )
)

REM Resolve tcp:// hostname to IP for container use
set DOCKER_HOST_ENV=
if defined DOCKER_HOST (
    echo !DOCKER_HOST! | findstr /B /C:"tcp://" >nul
    if !errorlevel!==0 (
        set "TCP_HOST_PORT=!DOCKER_HOST:tcp://=!"
        for /f "tokens=1,2 delims=:" %%A in ("!TCP_HOST_PORT!") do (
            set "TCP_HOST=%%A"
            set "TCP_PORT=%%B"
        )
        for /f "tokens=2 delims=[]" %%I in ('ping -n 1 -4 !TCP_HOST! 2^>nul ^| findstr /i "pinging"') do (
            set "RESOLVED_IP=%%I"
        )
        if defined RESOLVED_IP (
            set "DOCKER_HOST_ENV=-e DOCKER_HOST=tcp://!RESOLVED_IP!:!TCP_PORT!"
        )
    )
)

REM Mount DOCKER_CERT_PATH if set
set DOCKER_CERT_MOUNT=
set DOCKER_CERT_ENV=
if defined DOCKER_CERT_PATH (
    call :to_docker_path "!DOCKER_CERT_PATH!" CERT_DOCKER_PATH
    set "DOCKER_CERT_MOUNT=-v "!CERT_DOCKER_PATH!:!CERT_DOCKER_PATH!""
    set "DOCKER_CERT_ENV=-e DOCKER_CERT_PATH=!CERT_DOCKER_PATH!"
)

REM Dev mode: mount host .m2/repository
set DEV_MODE_MOUNT=
if %DEV_MODE%==1 (
    set "HOST_M2_REPO=%USERPROFILE%\.m2\repository"
    if not exist "!HOST_M2_REPO!\" (
        echo Error: Dev mode requires %%USERPROFILE%%\.m2\repository to exist.
        echo Run 'mvn install' on a project first to create it.
        exit /b 1
    )
    call :to_docker_path "!HOST_M2_REPO!" M2_DOCKER_PATH
    set "DEV_MODE_MOUNT=-v "!M2_DOCKER_PATH!:/kannich/dev-repo""
)

REM Run Kannich inside Docker
docker run --rm ^
    --init ^
    --privileged ^
    --name %CONTAINER_NAME% ^
    -v "%PROJECT_DOCKER_PATH%:/workspace" ^
    -v "%CACHE_DOCKER_PATH%:/kannich/cache" ^
    %DOCKER_SOCKET_MOUNT% ^
    %DOCKER_CERT_MOUNT% ^
    %DEV_MODE_MOUNT% ^
    %ENV_ARGS% ^
    %DOCKER_CERT_ENV% ^
    %DOCKER_HOST_ENV% ^
    -w /workspace ^
    %KANNICH_IMAGE% ^
    /kannich/jdk/bin/java -jar /kannich/kannich-cli.jar %*

endlocal
goto :eof

REM Convert Windows path to Docker format (D:\foo\bar -> /d/foo/bar)
:to_docker_path
set "_PATH=%~1"
set "_PATH=%_PATH:\=/%"
set "_DRIVE=%_PATH:~0,1%"
set "_REST=%_PATH:~2%"
set "%~2=/%_DRIVE%%_REST%"
goto :eof
