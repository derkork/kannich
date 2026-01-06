# Kannich Wrapper Script (PowerShell)
# Runs Kannich inside Docker - no local JVM required.
# Commit this file with your project to enable portable CI builds.

$ErrorActionPreference = "Stop"

# Capture all arguments (using $args to avoid PowerShell interpreting -v, -d, etc.)
$Arguments = $args

# Configuration
$KannichImage = if ($env:KANNICH_IMAGE) { $env:KANNICH_IMAGE } else { "derkork/kannich:latest" }
$DefaultEnvPrefixes = @("CI_", "GITHUB_", "BUILD_", "CIRCLE_", "TRAVIS_", "BITBUCKET_", "KANNICH_")

# Determine project directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = if ($env:PROJECT_DIR) { $env:PROJECT_DIR } else { $ScriptDir }

# Detect --dev-mode / -d flag
$DevMode = $false
foreach ($arg in $Arguments) {
    if ($arg -eq "--dev-mode" -or $arg -eq "-d") {
        $DevMode = $true
        break
    }
}

# Check for Docker
$dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCmd) {
    Write-Error "Error: Docker not found. Please install Docker."
    exit 1
}

$ErrorActionPreference = "Continue"
$null = docker info 2>&1
$dockerExitCode = $LASTEXITCODE
$ErrorActionPreference = "Stop"
if ($dockerExitCode -ne 0) {
    Write-Host "Error: Docker daemon not running. Please start Docker." -ForegroundColor Red
    exit 1
}

# Ensure cache exists
if ($env:KANNICH_CACHE_DIR) {
    $KannichCacheDir = $env:KANNICH_CACHE_DIR
    if (-not (Test-Path $KannichCacheDir)) {
        Write-Host "Creating cache directory: $KannichCacheDir"
        try {
            New-Item -ItemType Directory -Path $KannichCacheDir -Force | Out-Null
        } catch {
            Write-Error "Error: Failed to create cache directory: $KannichCacheDir"
            exit 1
        }
    }
    $KannichCacheDirDocker = $KannichCacheDir -replace '\\', '/'
    $CacheMount = @("-v", "${KannichCacheDirDocker}:/kannich/cache")
} else {
    $ErrorActionPreference = "Continue"
    $null = docker volume inspect kannich-cache 2>&1
    $volumeExists = $LASTEXITCODE -eq 0
    $ErrorActionPreference = "Stop"
    if (-not $volumeExists) {
        Write-Host "Creating docker volume: kannich-cache"
        docker volume create kannich-cache | Out-Null
    }
    $CacheMount = @("-v", "kannich-cache:/kannich/cache")
}

# Pull image if not present
$ErrorActionPreference = "Continue"
$null = docker image inspect $KannichImage 2>&1
$imageExists = $LASTEXITCODE -eq 0
$ErrorActionPreference = "Stop"
if (-not $imageExists) {
    Write-Host "Pulling Kannich builder image..."
    docker pull $KannichImage
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Error: Failed to pull image: $KannichImage"
        exit 1
    }
}

# Container name for cleanup
$ContainerName = "kannich-$PID"

# Read environment variable prefixes from .kannichenv or use defaults
$EnvPrefixes = @()
$kannichEnvFile = Join-Path $ProjectDir ".kannichenv"
if (Test-Path $kannichEnvFile) {
    Get-Content $kannichEnvFile | ForEach-Object {
        $line = $_ -replace '#.*$', ''  # Strip comments
        $line = $line.Trim()
        if ($line) {
            $EnvPrefixes += $line
        }
    }
} else {
    $EnvPrefixes = $DefaultEnvPrefixes
}

# Build environment variable arguments for docker
$EnvArgs = @()
foreach ($prefix in $EnvPrefixes) {
    Get-ChildItem env: | Where-Object { $_.Name -like "$prefix*" } | ForEach-Object {
        $EnvArgs += "-e"
        $EnvArgs += $_.Name
    }
}

# Always forward all DOCKER_* environment variables
Get-ChildItem env: | Where-Object { $_.Name -like "DOCKER_*" } | ForEach-Object {
    $EnvArgs += "-e"
    $EnvArgs += $_.Name
}

# Determine Docker socket mount (container is Linux, use Unix socket path)
$DockerSocketMount = @("-v", "//var/run/docker.sock:/var/run/docker.sock")
if ($env:DOCKER_HOST) {
    if ($env:DOCKER_HOST -like "unix://*") {
        # Extract socket path from unix:///path/to/socket
        $socketPath = $env:DOCKER_HOST -replace "^unix://", ""
        $DockerSocketMount = @("-v", "${socketPath}:${socketPath}")
    } elseif ($env:DOCKER_HOST -like "tcp://*" -or $env:DOCKER_HOST -like "npipe://*") {
        # TCP or named pipe connection - no socket mount needed
        $DockerSocketMount = @()
    }
}

# Mount DOCKER_CERT_PATH if set
$DockerCertMount = @()
if ($env:DOCKER_CERT_PATH) {
    $DockerCertMount = @("-v", "${env:DOCKER_CERT_PATH}:${env:DOCKER_CERT_PATH}")
}

# Resolve tcp:// hostname to IP for container use
$DockerHostEnv = @()
if ($env:DOCKER_HOST -like "tcp://*") {
    $tcpHostPort = $env:DOCKER_HOST -replace "^tcp://", ""
    $parts = $tcpHostPort -split ":"
    $tcpHost = $parts[0]
    $tcpPort = $parts[1]

    try {
        $resolved = [System.Net.Dns]::GetHostAddresses($tcpHost) | Where-Object { $_.AddressFamily -eq 'InterNetwork' } | Select-Object -First 1
        if ($resolved) {
            $DockerHostEnv = @("-e", "DOCKER_HOST=tcp://$($resolved.IPAddressToString):$tcpPort")
        }
    } catch {
        # If resolution fails, don't set the env var
    }
}

# Cleanup function
function Stop-KannichContainer {
    param([string]$Name)
    if ($Name) {
        $null = docker stop $Name 2>&1
    }
}

# Dev mode: mount host .m2/repository
$DevModeMount = @()
if ($DevMode) {
    $hostM2Repo = Join-Path $env:USERPROFILE ".m2\repository"
    if (-not (Test-Path $hostM2Repo)) {
        Write-Error "Error: Dev mode requires ~/.m2/repository to exist."
        Write-Host "Run 'mkdir -p ~/.m2/repository' or 'mvn install' on a project first."
        exit 1
    }
    $DevModeMount = @("-v", "${hostM2Repo}:/kannich/dev-repo")
}

# Convert Windows paths to Docker-compatible format
$ProjectDirDocker = $ProjectDir -replace '\\', '/'

# Build the docker command arguments
$dockerArgs = @(
    "run", "--rm",
    "--init",
    "--name", $ContainerName,
    "--privileged",
    "-v", "${ProjectDirDocker}:/workspace"
)
$dockerArgs += $CacheMount

$dockerArgs += $DockerSocketMount
$dockerArgs += $DockerCertMount
$dockerArgs += $DevModeMount
$dockerArgs += $EnvArgs
$dockerArgs += $DockerHostEnv
$dockerArgs += @("-w", "/workspace")
$dockerArgs += $KannichImage
$dockerArgs += @("/kannich/jdk/bin/java", "-jar", "/kannich/kannich-cli.jar")
$dockerArgs += $Arguments

# Run Kannich inside Docker
$ErrorActionPreference = "Continue"
& docker $dockerArgs
exit $LASTEXITCODE
