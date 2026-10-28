# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.10.0] - 2026-05-15
### Added
- Tests are now written as Kannich scripts (`.tests.main.kts`) using the new `testSuite { ... }` DSL, with `test`, `group`, `beforeAll`, `beforeEach`, `afterEach` and `afterAll` blocks. Tests now run inside Kannich itself instead of a separately managed test container.
- New helpers `verify`, `verifyFail` and `clearCaches` for writing test assertions and resetting tool caches.
- Tests can be limited to a single group by setting the `KANNICH_TEST_GROUP` environment variable. Setting `KANNICH_TEST_CLEAR` makes `clearCaches` actually clear the caches.

### Breaking Change
- The Kotest/Testcontainers based test infrastructure (`KannichTestSpecExtension`, `PipelineBuilder` and `ContainerExecutor`) has been removed. Migrate your tests to the `testSuite` DSL.

## [0.9.0] - 2026-03-20
### Changed
- Bumped the `kannich-parent` dependency to `0.9.0`.

### Fixed
- Testcontainers and the Kotest Testcontainers extension are now regular dependencies, so modules using `kannich-test` no longer need to declare them themselves.

## [0.8.0] - 2026-03-06
### Changed
- Update parent pom version.

## [0.7.0] - 2026-02-25
### Fixed
- `ContainerExecutor.runKannich` can now be called multiple times in the same container.

## [0.6.0] - 2026-02-20
### Changed
- Update parent pom version.

## [0.5.0] - 2026-02-05
- Initial release
