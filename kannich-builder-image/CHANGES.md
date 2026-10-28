# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.12.1] - 2026-10-28
### Security
- Updated the base image to `cruizba/ubuntu-dind:noble-29.8.2` (Docker 29.8.2), fixing a number of vulnerabilities in the Docker binaries and OpenSSL. The base image is now pinned by digest.
- Removed the unused duplicate `docker-buildx-plugin` and `docker-compose-plugin` packages as well as the unused `docker-model-plugin` package to reduce the attack surface. `docker buildx` and `docker compose` continue to work.
- Updated Kannich to `0.10.1`, which fixes vulnerabilities in the bundled `guava` (CVE-2026-102554) and `plexus-utils` (CVE-2025-67030) libraries.

## [0.12.0] - 2026-08-28
### Fixed
- Quoted arguments to the kannichw wrapper are now properly forwarded to the kannich binary in container.
