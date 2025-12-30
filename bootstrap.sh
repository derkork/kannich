#!/bin/bash
# Kannich Developer Bootstrap Script
# Builds Kannich from source and creates the builder Docker image.
# Requirements: JDK 21+, Maven 3.9+, Docker

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Kannich Bootstrap ==="
echo ""

# Check prerequisites
echo "Checking prerequisites..."

if ! command -v java &> /dev/null; then
    echo "Error: Java not found. Please install JDK 21 or later."
    exit 1
fi

JAVA_VERSION_FULL=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
JAVA_VERSION=$(echo "$JAVA_VERSION_FULL" | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "Error: Java 21 or later required. Found: $JAVA_VERSION_FULL"
    exit 1
fi
echo "  Java: $JAVA_VERSION_FULL"

if ! command -v mvn &> /dev/null; then
    echo "Error: Maven not found. Please install Maven 3.9 or later."
    exit 1
fi
echo "  Maven: found"

if ! command -v docker &> /dev/null; then
    echo "Error: Docker not found. Please install Docker."
    exit 1
fi

if ! docker info &> /dev/null; then
    echo "Error: Docker daemon not running. Please start Docker."
    exit 1
fi
echo "  Docker: found"

echo ""
echo "Building and installing Kannich to local repository..."
mvn clean install -DskipTests -q

CLI_JAR="kannich-cli/target/kannich-cli-0.1.0-SNAPSHOT.jar"
if [ ! -f "$CLI_JAR" ]; then
    echo "Error: CLI jar not found at $CLI_JAR"
    echo "Maven build may have failed. Try running: mvn clean install"
    exit 1
fi

echo ""
echo "Preparing Docker build context..."
cp "$CLI_JAR" kannich-builder-image/kannich-cli.jar

# Copy Kannich artifacts from local .m2 for offline Docker builds
M2_KANNICH="$HOME/.m2/repository/dev/kannich"
if [ -d "$M2_KANNICH" ]; then
    echo "Copying Kannich libraries for offline Docker builds..."
    rm -rf kannich-builder-image/m2-repo
    mkdir -p kannich-builder-image/m2-repo/dev/kannich
    cp -r "$M2_KANNICH"/* kannich-builder-image/m2-repo/dev/kannich/
fi

echo ""
echo "Building Docker image..."
if ! docker build -t kannich/builder:latest ./kannich-builder-image; then
    rm -f kannich-builder-image/kannich-cli.jar
    rm -rf kannich-builder-image/m2-repo
    echo "Error: Docker image build failed."
    exit 1
fi

# Clean up
rm -f kannich-builder-image/kannich-cli.jar
rm -rf kannich-builder-image/m2-repo

echo ""
echo "=== Bootstrap Complete ==="
echo ""
echo "Kannich is ready. You can now use:"
echo "  java -jar kannich-cli/target/kannich-cli-*.jar <execution>"
echo ""
echo "Or build projects with the wrapper:"
echo "  ./kannichw <execution>"
