# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.10.0] - 2026-05-15
### Added
- New `ArchiveToolInstaller` base class that plugin authors can extend to build tools that install from a downloaded archive, with built-in caching and architecture awareness.
- New `Compressor.compress` method for creating archives (tar.gz, tar.xz, tar.bz2, zip, gz).
- `Compressor.extract` now supports a `stripComponents` parameter to strip leading path components from the archive (equivalent to `tar --strip-components`).

### Changed
- `Apt.install` now uses plain `apt-get install -y` directly. The previous implementation attempted per-package caching with dependency resolution via dpkg, but the added complexity outweighed the benefit.
- `Web.download` no longer maintains a persistent download cache. Each call downloads the file fresh. Tool installers that previously relied on the download cache now rely on the tool-level cache instead, so repeated pipeline runs are unaffected.

## [0.9.0] - 2026-03-20
### Changed
- Bumped the `kannich-parent` dependency to `0.9.0`.
- Removed unnecessary dependencies slowing down module loading.

## [0.8.0] - 2026-03-06
### Changed
- Update parent pom version.

## [0.7.0] - 2026-02-25
### Changed
- Update parent pom version.

## [0.6.0] - 2026-02-20
### Added
- `Fs` tool now has support for `chmod`.
- `Fs` tool now has a new method `getParent` which returns the parent directory of a given path.
- `Docker` tool now has a new method `tagAndPush` which tags and pushes a given image.

## [0.5.0] - 2026-02-05
- Initial release