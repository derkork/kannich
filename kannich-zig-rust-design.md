# kannich-zig / kannich-rust Design Notes

## Status

Implementation is partially done. There is a bug to fix before it works correctly.

## What has been implemented

### kannich-zig (new module)
- `kannich-zig/pom.xml` created, added to root `pom.xml` modules list
- `Zig(version: String)` tool in `kannich-zig/src/main/kotlin/dev/kannich/zig/Zig.kt`
  - Downloads and caches Zig tarball from `https://ziglang.org/download/$version/zig-linux-x86_64-$version.tar.xz`
  - `getToolPaths()` adds the zig binary directory to PATH
  - `exec()` for running zig commands directly
  - `withToolchain { }` sets `CC`, `CXX`, `AR` env vars to use `zig cc` / `zig c++` / `zig ar`

### kannich-rust changes
- `Zig` is now a **required** first argument to `Cargo` (was optional, made required — see rationale below)
- `Cargo(zig: Zig, version: String? = null)`
- `withRustEnv` wraps execution in `zig.withToolchain { }` so all cargo commands get the Zig env vars
- `kannich-rust/pom.xml` depends on `kannich-zig:0.1.0`
- Integration tests updated to use `Cargo(Zig("0.13.0"))` and `Cargo(Zig("0.13.0"), "1.85.0")`

## Known bug: Rust ignores CC for its own linker

**The current implementation does not work correctly for Rust.**

Setting `CC=zig cc` only affects C code compiled by `build.rs` scripts (via the `cc` crate).
Cargo uses a **separate linker** for linking the final Rust binary, configured via
`CARGO_TARGET_<triple>_LINKER` or `RUSTFLAGS=-C linker=...`. This still defaults to the
system `cc`, which is not present in the Kannich container — hence the warning:

```
warn: no default linker (`cc`) was found in your PATH
```

## Decided solution: wrapper scripts exposed via PATH

`Zig.ensureInstalled()` writes wrapper scripts `cc`, `c++`, and `ar` into
`${zigHome()}/wrappers/`, each delegating to the full path of the `zig` binary:

```sh
#!/bin/sh
exec /path/to/zig/zig cc "$@"
```

`Zig.getToolPaths()` returns both `zigHome()` and `${zigHome()}/wrappers/`, so the
wrappers are on PATH whenever `withTools(zig)` is used.

`Zig.withToolchain` uses `JobContext.withTools(this)` (which adds both dirs to PATH
and calls `ensureInstalled()`) and then sets `CC=cc`, `CXX=c++`, `AR=ar`:

```kotlin
val ctx = JobContext.current()
return ctx.withTools(this) {
    ctx.withEnv(mapOf("CC" to "cc", "CXX" to "c++", "AR" to "ar")) { block() }
}
```

**`kannich-zig` has no Rust knowledge.** The `RUSTFLAGS` plumbing is handled in
`Cargo.withRustEnv`, which wraps `zig.withToolchain` and appends `-C linker=cc`
to `RUSTFLAGS` (since `cc` is already on PATH at that point):

```kotlin
return zig.withToolchain {
    val existing = JobContext.current().env["RUSTFLAGS"] ?: ""
    val rustflags = "$existing -C linker=cc".trim()
    JobContext.current().withEnv(env + mapOf("RUSTFLAGS" to rustflags)) { block() }
}
```

This makes Cargo use Zig as its linker for the final binary, not just for build scripts.

### Why not cargo-zigbuild?
- Beta quality, frequent breakage on new Zig versions
- Silent failure modes (glibc version silently downgrades, security hardening flags silently dropped)
- Known incompatibilities with `ring`, `aws-lc-rs`
- The wrapper script handles the native Linux build case cleanly without those risks

### Why not a system C toolchain (apt-get)?
`Apt.install()` in `kannich-tools` is available and does cache `.deb` files, so it can be
used both internally by Kannich modules and directly in user pipelines. However it lacks the
integration level of a proper Kannich tool: no `getToolPaths()`, no `withToolchain {}`, no
version-pinned reproducible installs, and no cross-compilation support. `build-essential`
gives native gcc only — cross-compiling to arm64 Linux or Windows would require separate
packages (`gcc-aarch64-linux-gnu`, `mingw-w64`, etc.) with version drift, and macOS would
still need osxcross on top. Zig covers all Linux and Windows targets with a single
version-pinned binary, making it the right choice for the toolchain layer.

## kannich-zig is a general-purpose C/C++ toolchain, not just a Rust helper

`kannich-zig` is designed as a first-class tool for any C/C++ build, not just Rust.
The goal is a portable, cacheable C/C++ toolchain that works for any project — CMake,
SCons, Meson, plain Makefiles, etc. A key motivating use case is **Godot GDExtension builds**
(C++ via godot-cpp, built with SCons via `kannich-uv`), but it is not limited to that.

For C/C++ projects, `zig.withToolchain { }` is used directly without any Rust involvement:

```kotlin
val zig = Zig("0.13.0")
val build = job("Build") {
    zig.withToolchain {
        Shell.exec("scons", "platform=linux")
    }
}
```

For C/C++ tools (SCons, CMake, etc.), `CC=zig cc` covers both compilation and linking since
the compiler driver also invokes the linker — the wrapper script issue is Rust-specific.

## Cross-compilation

### Kannich always runs in a Linux container

The `kannichw` wrapper script always starts a Linux Docker container, regardless of the host OS.
Even on macOS, Docker Desktop runs Linux containers inside a VM. There is no "native host"
escape hatch — Kannich is always cross-compiling when targeting macOS or Windows.

### Target matrix

| Target | Toolchain | Self-contained? |
|---|---|---|
| Linux (any arch) | Zig (`-target <triple>`) | Yes — Zig ships libc for all Linux targets |
| Windows (MinGW ABI) | Zig (`-target x86_64-windows-gnu`) | Yes |
| macOS | osxcross + macOS SDK | No — Apple SDK required (see below) |
| Windows (MSVC ABI) | Out of scope | Requires Windows host |

### Linux and Windows: Zig handles it

For Linux and MinGW Windows targets, `Zig.withToolchain` generalises to cross-compilation
by embedding `-target <zig-triple>` in the wrapper scripts. No external dependencies needed.
Design for this is TBD (see open questions below).

### macOS: osxcross is required (hard dependency for Godot)

Zig can cross-compile to macOS with `-isysroot /path/to/sdk`, but this is not sufficient
for Godot GDExtension builds. Analysis of `platform/macos/detect.py` in Godot's source shows:

- **`OSXCROSS_ROOT` is a hard gate**: `can_build()` returns `False` unless this env var is set.
  Generic `CC`/`CXX` are ignored for macOS cross-compilation.
- **osxcross-named binaries are hardcoded**: Godot constructs paths like
  `{OSXCROSS_ROOT}/target/bin/arm64-apple-{sdk}-cc` directly.
- **`lipo` is required**: called to create universal binaries (fat binaries with x86_64 + arm64).
  osxcross ships this as `llvm-lipo`.
- **`xcrun` shim**: osxcross provides a stub that satisfies calls from the build system.

godot-cpp (used by GDExtensions) uses the same SCons-based detection as the engine.

### macOS SDK source

The macOS SDK cannot be bundled — Apple's license prohibits redistribution.
[joseluisq/macosx-sdks](https://github.com/joseluisq/macosx-sdks) hosts pre-extracted SDKs
(macOS 12.3 through 15.x) as GitHub release tarballs, downloadable without authentication.
This is the de-facto standard for automated CI pipelines and widely used in open source projects.

### `kannich-osxcross` design

This is a new module, separate from `kannich-zig`.

**Decided: build osxcross from source, cache the result.**

Pre-built osxcross distributions were evaluated and rejected:
- `crossmac` — unmaintained since 2022, low trust
- `pts-osxcross` — no Apple Silicon support, ancient SDK
- `crazymax/docker-osxcross` — actively maintained but Docker-image-only; Kannich's
  internal Docker daemon does not survive between runs so images are not cacheable

Building from source is a one-time cost — the result is stored in the Kannich cache
volume and reused on all subsequent runs.

**Can osxcross use Zig as its clang backend?**

osxcross's `build.sh` can be pointed at an existing clang rather than compiling LLVM.
Since `kannich-zig` already provides a clang-compatible compiler (`zig cc`), `OsxCross`
should depend on `Zig` and pass it as the clang backend. This avoids downloading and
compiling LLVM entirely — the heavy part of the osxcross build disappears.

This needs to be validated: confirm that osxcross's `build.sh` accepts a `zig`-backed
clang and that the resulting wrapper scripts work correctly for Godot's SCons build.

**What build tools does `build.sh` need?**

osxcross's build script requires `cmake`, `make`, and `patch`.
- `bzip2` and `xz`: Ubuntu's `tar` has both built in, no standalone binaries needed
- `python3`: provided by `kannich-uv`, no system Python needed

These are installed via `Apt.install("cmake", "make", "patch")` inside
`OsxCross.ensureInstalled()`. `Apt` already implements per-package `.deb` caching
(`kannich-tools/src/main/kotlin/dev/kannich/tools/Apt.kt`) — packages are downloaded
once, cached in the Kannich cache volume, and reinstalled from cache on subsequent runs.
No changes to the builder image Dockerfile are needed.

**Module structure**

Two tools, each independently cacheable:

`MacOSSDK(version: String)`
- Downloads SDK tarball from `joseluisq/macosx-sdks` GitHub releases
- Extracts and caches at `macos-sdk/MacOSX{version}.sdk`
- Standalone: also usable directly with `Zig` for non-Godot C++ projects

`OsxCross(zig: Zig, sdk: MacOSSDK, version: String)`
- Downloads osxcross source from `tpoechtrager/osxcross` at the given tag/commit
- Places the SDK tarball into osxcross's `tarballs/` directory
- Runs `build.sh` with `zig cc` as the clang backend
- Caches the resulting `target/` directory
- `getToolPaths()` returns `target/bin` so all named wrappers are on PATH
- `withToolchain(block)` calls `withTools(this)` and sets `OSXCROSS_ROOT`

**API**

```kotlin
val zig = Zig("0.13.0")
val sdk = MacOSSDK("14.2")
val osxcross = OsxCross(zig, sdk, "da4f2f4")   // osxcross commit hash

val build = job("Build macOS") {
    osxcross.withToolchain {
        Shell.exec("scons", "platform=macos", "arch=arm64")
    }
}
```

`OSXCROSS_ROOT` is set inside `withToolchain`, satisfying Godot's `detect.py` hard gate.
`lipo` is provided by osxcross's own LLVM tools in `target/bin`.

**osxcross versioning**

osxcross has no stable release tags — the convention is to pin a commit hash.
The `version` parameter accepts a commit hash. A recommended/tested hash
should be documented alongside the `MacOSSDK` version it was validated with.
This is the same pattern as all other Kannich tools requiring an explicit version.

## Design rationale: why Zig is required (not optional)

Even pure Rust projects need a linker. While Rust 1.90+ ships `rust-lld` and no longer needs
a system linker for native x86_64 Linux builds, in practice virtually all non-trivial projects
pull in C dependencies transitively (TLS stack, crypto, etc.). Making `Zig` required avoids
a class of confusing failures and makes the dependency explicit.

## Other design decisions made

### No default Zig version
`Zig(version)` requires an explicit version, same pattern as all other Kannich tools.
The Godot community in particular has specific tested Zig versions, so defaulting to
"latest" would be dangerous.

### SCons via kannich-uv
There is no `kannich-scons` tool and none should be created. SCons is a Python tool
and is installed and run via `kannich-uv`.

### AR is included in withToolchain
`withToolchain` sets `CC`, `CXX`, and `AR` (`zig ar`). `AR` is needed for C/C++
projects that produce or consume static libraries.

### Target triples deliberately not abstracted
We discussed the mismatch between Zig, Rust/Cargo, and Godot/SCons target triple
naming conventions. The decision was to **not** abstract this in v1. Users pass
`-target <zig-triple>` themselves when cross-compiling. This is a known gap, not
an oversight.

## Next steps

### Native build fix (ready to implement)
- In `Zig.ensureInstalled()`: write `cc`, `c++`, `ar` wrapper scripts into `${zigHome()}/wrappers/`
- Update `Zig.getToolPaths()` to also return `${zigHome()}/wrappers/`
- Rewrite `Zig.withToolchain` to use `withTools(this)` + `withEnv(CC/CXX/AR)`
- In `Cargo.withRustEnv`: append `-C linker=cc` to `RUSTFLAGS` after `zig.withToolchain`

### `kannich-osxcross` (design complete, not yet implemented)
- Validate that osxcross `build.sh` accepts `zig cc` as the clang backend
- Implement `MacOSSDK(version)` — downloads from `joseluisq/macosx-sdks`, caches
- Implement `OsxCross(zig, sdk, version)` — builds osxcross from source, caches `target/`
- Integration test against a Godot GDExtension macOS build

### Linux/Windows cross-compilation via Zig (not yet designed)
- How `Zig.withToolchain` should expose a target triple is TBD
- Likely: optional `target` parameter that gets embedded in the wrapper scripts
