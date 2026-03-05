# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0] - 2026-03-05
### Added
- The `exec` command now accepts a `silent` flag which prevents the output from appearing in the Kannich logs. It also returns an `ExecResult` object containing the command's output and exit code.

## [0.2.0] - 2026-03-04
### Fixed
- UV python installations are now properly cached.
- UV link mode is set to "copy" by default to work with Kannich's overlayfs.

## [0.1.1] - 2026-03-03
### Fixed
- Fixed location of UV cache path.

## [0.1.0] - 2026-03-03
### Added
- Initial version.
