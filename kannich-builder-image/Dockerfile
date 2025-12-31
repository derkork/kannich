# Kannich Builder Image
# Base image for running Kannich CI builds
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
    # Build essentials
    build-essential \
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
    # Network tools
    netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

# Install Docker CLI (client only, for interfacing with external Docker daemon)
RUN curl -fsSL https://download.docker.com/linux/static/stable/x86_64/docker-27.4.1.tgz -o /tmp/docker.tgz \
    && tar -xzf /tmp/docker.tgz -C /tmp \
    && mv /tmp/docker/docker /usr/local/bin/ \
    && rm -rf /tmp/docker /tmp/docker.tgz \
    # Install Docker Compose plugin
    && mkdir -p /usr/local/lib/docker/cli-plugins \
    && curl -fsSL https://github.com/docker/compose/releases/download/v2.32.4/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose \
    && chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Install Eclipse Temurin JDK 21 to private path (not in global PATH)
# This allows build pipelines to install their own Java without conflicts
RUN curl -fsSL -o /tmp/jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" \
    && mkdir -p /kannich/jdk \
    && tar -xzf /tmp/jdk.tar.gz -C /kannich/jdk --strip-components=1 \
    && rm /tmp/jdk.tar.gz

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
