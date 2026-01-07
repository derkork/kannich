@echo off
setlocal enabledelayedexpansion

:: Kannich Wrapper Script
:: Commit this file with your project to enable portable CI builds.

:: Configuration
if "%KANNICH_IMAGE%"=="" set "KANNICH_IMAGE=derkork/kannich:latest"

:: Determine project directory
set "SCRIPT_DIR=%~dp0"
:: Remove trailing backslash if present
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
if "%PROJECT_DIR%"=="" set "PROJECT_DIR=%SCRIPT_DIR%"

:: Check for Docker
where docker >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: Docker not found. Please install Docker. >&2
    exit /b 1
)

docker info >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: Docker daemon not running. Please start Docker. >&2
    exit /b 1
)

if not "%KANNICH_CACHE_DIR%"=="" (
    :: user may want to supply their own cache dir
    if not exist "%KANNICH_CACHE_DIR%" (
        echo Creating cache directory: %KANNICH_CACHE_DIR%
        mkdir "%KANNICH_CACHE_DIR%"
        if %ERRORLEVEL% neq 0 (
            echo Error: Failed to create cache directory: %KANNICH_CACHE_DIR% >&2
            exit /b 1
        )
    )
    set "CACHE_MOUNT=%KANNICH_CACHE_DIR%:/kannich/cache"
) else (
    :: the default cache is a docker volume
    docker volume inspect kannich-cache >nul 2>&1
    if %ERRORLEVEL% neq 0 (
        echo Creating docker volume: kannich-cache
        docker volume create kannich-cache >nul
    )
    set "CACHE_MOUNT=kannich-cache:/kannich/cache"
)

:: Detect --dev-mode / -d flag
set DEV_MODE=0
for %%a in (%*) do (
    if "%%a"=="--dev-mode" set DEV_MODE=1
    if "%%a"=="-d" set DEV_MODE=1
)

:: Dev mode: mount host .m2/repository
set "DEV_MODE_MOUNT="
if "%DEV_MODE%"=="1" (
    set "HOST_M2_REPO=%USERPROFILE%\.m2\repository"
    if not exist "!HOST_M2_REPO!" (
        echo No local maven repository exists. Creating an empty one.
        mkdir "!HOST_M2_REPO!"
        if !ERRORLEVEL! neq 0 (
            echo Error: Failed to create local maven repository: !HOST_M2_REPO! >&2
            exit /b 1
        )
    )
    set "DEV_MODE_MOUNT=-v "!HOST_M2_REPO!":/kannich/dev-repo"
)

:: Pull image if not present
docker image inspect "%KANNICH_IMAGE%" >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Pulling Kannich builder image...
    docker pull "%KANNICH_IMAGE%"
)

:: Container name for cleanup
set "CONTAINER_NAME=kannich-%RANDOM%"

:: Put all environment variables into a file named ".kannich_current_env"
set > "%PROJECT_DIR%\.kannich_current_env"

:: Run Kannich inside Docker
docker run --rm ^
    --init ^
    --name "%CONTAINER_NAME%" ^
    --privileged ^
    -v "%PROJECT_DIR%:/workspace" ^
    -v "%CACHE_MOUNT%" ^
    %DEV_MODE_MOUNT% ^
    -w /workspace ^
    "%KANNICH_IMAGE%" ^
    %*

exit /b %ERRORLEVEL%
