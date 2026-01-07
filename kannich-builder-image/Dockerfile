# Kannich Builder Image
# Base image for running Kannich CI builds

# Stage 1: Build minimal JDK using jlink
FROM eclipse-temurin:21-jdk AS jdk-builder
RUN jlink \
    --add-modules java.base,java.compiler,java.instrument,java.logging,java.management,java.naming,java.net.http,java.desktop,java.security.jgss,java.sql,java.xml,jdk.compiler,jdk.unsupported,jdk.zipfs,jdk.crypto.ec \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=zip-6 \
    --output /jdk-minimal

# Stage 2: Final image
FROM cruizba/ubuntu-dind:noble-29.1.3

LABEL org.opencontainers.image.title="Kannich Builder"
LABEL org.opencontainers.image.description="Base image for Kannich CI builds"
LABEL org.opencontainers.image.source="https://github.com/kannich/kannich"

# Avoid interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install essential build tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    # Core tools
    curl \
    wget \
    git \
    ca-certificates \
    # GPG for code signing
    gnupg2 \
    # File system utilities for overlay support
    fuse-overlayfs \
    rsync \
    # Archive handling
    tar \
    gzip \
    unzip \
    zip \
    # Text processing
    jq \
    yq \
    # Network tools
    netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

# Copy minimal JDK from builder stage (not in global PATH)
# This allows build pipelines to install their own Java without conflicts
COPY --from=jdk-builder /jdk-minimal /kannich/jdk

# JAVA_HOME for kannich's private JDK - NOT added to PATH intentionally
ENV KANNICH_JAVA_HOME=/kannich/jdk

# Create Kannich directories
RUN mkdir -p /kannich/cache /kannich/overlays /workspace

# Copy Kannich CLI jar
COPY kannich-cli.jar /kannich/kannich-cli.jar

# Copy helper scripts
COPY scripts/ /kannich/scripts/
RUN chmod +x /kannich/scripts/*

# Set working directory
WORKDIR /workspace

# Use entrypoint script for kannich invocation
ENTRYPOINT ["/kannich/scripts/entrypoint.sh"]
