# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0] - 2026-03-20
### Changed
- Bumped the `kannich-parent` dependency to `0.9.0`.
- Removed unnecessary dependencies slowing down module loading.

## [0.3.1] - 2026-03-11
### Fixed
- The plugin now uses the correct `kannich-maven` version.

## [0.3.0] - 2026-03-06
### Breaking Change
- The `Quarkus` class has been removed. Quarkus build steps are now extension methods on the `Maven` class.

## [0.2.0] - 2026-02-25
### Changed
- Update parent pom version.

## [0.1.0] - 2026-02-20
### Added
- Initial release of the Quarkus module.
