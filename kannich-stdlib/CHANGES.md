# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.7.0] - 2026-02-25
### Changed
- Update parent pom version.

## [0.6.0] - 2026-02-20
### Added
- `FsUtil` now has support for `chmod`.
- A new `Tool` interface was added to allow for a standardized way to add new tools to the library.
- `JobContext` has a new function `withTools` which adds tools to the `PATH` environment variable.

## [0.5.0] - 2026-02-05
- Initial release