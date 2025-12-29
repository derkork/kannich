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
    gnupg \
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

# Install Eclipse Temurin JDK 21
RUN curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo $VERSION_CODENAME) main" > /etc/apt/sources.list.d/adoptium.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends temurin-21-jdk \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Install Maven 3.9.x
ENV MAVEN_VERSION=3.9.9
ENV MAVEN_HOME=/opt/maven
RUN curl -fsSL https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    | tar xz -C /opt \
    && mv /opt/apache-maven-${MAVEN_VERSION} ${MAVEN_HOME}
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

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

# Default command keeps container running for exec
CMD ["tail", "-f", "/dev/null"]
