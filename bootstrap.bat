@echo off
REM Kannich Developer Bootstrap Script
REM Builds Kannich from source and creates the builder Docker image.
REM Requirements: JDK 21+, Maven 3.9+, Docker

setlocal enabledelayedexpansion

echo === Kannich Bootstrap ===
echo.

REM Check prerequisites
echo Checking prerequisites...

where java >nul 2>&1
if errorlevel 1 (
    echo Error: Java not found. Please install JDK 21 or later.
    exit /b 1
)

for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION=%%g
)
set JAVA_VERSION=%JAVA_VERSION:"=%
for /f "tokens=1 delims=." %%a in ("%JAVA_VERSION%") do set JAVA_MAJOR=%%a
if %JAVA_MAJOR% LSS 21 (
    echo Error: Java 21 or later required. Found: %JAVA_VERSION%
    exit /b 1
)
echo   Java: %JAVA_VERSION%

where mvn >nul 2>&1
if errorlevel 1 (
    echo Error: Maven not found. Please install Maven 3.9 or later.
    exit /b 1
)
echo   Maven: found

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
echo   Docker: found

echo.
echo Building and installing Kannich to local repository...
call mvn clean install -DskipTests -q
if errorlevel 1 (
    echo Error: Maven build failed.
    exit /b 1
)

set CLI_JAR=kannich-cli\target\kannich-cli-0.1.0-SNAPSHOT.jar
if not exist "%CLI_JAR%" (
    echo Error: CLI jar not found at %CLI_JAR%
    echo Maven build may have failed. Try running: mvn clean install
    exit /b 1
)

echo.
echo Preparing Docker build context...
copy "%CLI_JAR%" kannich-builder-image\kannich-cli.jar >nul

REM Copy Kannich artifacts from local .m2 for offline Docker builds
set M2_KANNICH=%USERPROFILE%\.m2\repository\dev\kannich
if exist "%M2_KANNICH%" (
    echo Copying Kannich libraries for offline Docker builds...
    if exist kannich-builder-image\m2-repo rmdir /s /q kannich-builder-image\m2-repo
    mkdir kannich-builder-image\m2-repo\dev\kannich
    xcopy /s /e /q "%M2_KANNICH%" kannich-builder-image\m2-repo\dev\kannich\
)

echo.
echo Building Docker image...
docker build -t kannich/builder:latest ./kannich-builder-image
if errorlevel 1 (
    del kannich-builder-image\kannich-cli.jar >nul 2>&1
    rmdir /s /q kannich-builder-image\m2-repo >nul 2>&1
    echo Error: Docker image build failed.
    exit /b 1
)

REM Clean up
del kannich-builder-image\kannich-cli.jar >nul 2>&1
rmdir /s /q kannich-builder-image\m2-repo >nul 2>&1

echo.
echo === Bootstrap Complete ===
echo.
echo Kannich is ready. You can now use:
echo   java -jar kannich-cli\target\kannich-cli-*.jar ^<execution^>
echo.
echo Or build projects with the wrapper:
echo   kannichw ^<execution^>

endlocal
