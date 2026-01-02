# Kannich Builder Image
# Base image for running Kannich CI builds

# Stage 1: Build minimal JDK using jlink
FROM eclipse-temurin:21-jdk AS jdk-builder
RUN jlink \
    --add-modules java.base,java.compiler,java.instrument,java.logging,java.management,java.naming,java.net.http,java.security.jgss,java.sql,java.xml,jdk.compiler,jdk.unsupported,jdk.zipfs,jdk.crypto.ec \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=zip-6 \
    --output /jdk-minimal

# Stage 2: Final image
FROM ubuntu:24.04

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

# Install Docker CLI (client only, for interfacing with external Docker daemon)
RUN curl -fsSL https://download.docker.com/linux/static/stable/x86_64/docker-29.1.3.tgz -o /tmp/docker.tgz \
    && tar -xzf /tmp/docker.tgz -C /tmp \
    && mv /tmp/docker/docker /usr/local/bin/ \
    && rm -rf /tmp/docker /tmp/docker.tgz \
    # Install Docker CLI plugins
    && mkdir -p /usr/local/lib/docker/cli-plugins \
    && curl -fsSL https://github.com/docker/compose/releases/download/v5.0.1/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose \
    && curl -fsSL https://github.com/docker/buildx/releases/download/v0.30.1/buildx-v0.30.1.linux-amd64 -o /usr/local/lib/docker/cli-plugins/docker-buildx \
    && chmod +x /usr/local/lib/docker/cli-plugins/docker-compose /usr/local/lib/docker/cli-plugins/docker-buildx

# Copy minimal JDK from builder stage (not in global PATH)
# This allows build pipelines to install their own Java without conflicts
COPY --from=jdk-builder /jdk-minimal /kannich/jdk

# JAVA_HOME for kannich's private JDK - NOT added to PATH intentionally
ENV KANNICH_JAVA_HOME=/kannich/jdk

# Create Kannich directories
RUN mkdir -p /kannich/cache /kannich/overlays /workspace /root/.m2/repository

# Copy Kannich CLI jar
COPY kannich-cli.jar /kannich/kannich-cli.jar

# Copy pre-built Kannich libraries for offline builds
# (These are populated from local .m2 during bootstrap)
COPY m2-repo/ /root/.m2/repository/

# Copy helper scripts
COPY scripts/ /kannich/scripts/
RUN chmod +x /kannich/scripts/*

# Set working directory
WORKDIR /workspace

# Use entrypoint script for kannich invocation
ENTRYPOINT ["/kannich/scripts/entrypoint.sh"]
