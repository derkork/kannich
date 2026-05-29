+++
title = "Working with Files"
weight = 2
+++

## Introduction

This guide covers the `Fs` and `Compressor` tools for filesystem operations and archive handling, and how to scope work to a subdirectory with `cd { }`.

## Changing the Working Directory with `cd`

By default every operation inside a job runs relative to the project root. `cd { }` scopes a block of work to a subdirectory without affecting anything outside the block:

```kotlin
job {
    cd("modules/app") {
        // working directory is now modules/app
        maven.exec("package")
        // working directory is restored after the block
    }
    // back at the project root
}
```

The path is always relative to the *current* working directory, so nesting works naturally:

```kotlin
cd("modules") {
    cd("app") {
        // working directory is modules/app
    }
}
```

`cd` returns whatever the block returns, so you can use it to read a value from a subdirectory without affecting the rest of the job:

```kotlin
val version = cd("modules/app") {
    maven.getProjectVersion()
}
```

## Working with the Filesystem

The `Fs` tool covers the most common filesystem operations. All paths are relative to the current working directory (respecting any enclosing `cd { }` block).

### Creating Directories

```kotlin
Fs.mkdir("dist/bin")
```

Creates the directory and all missing parent directories. Does nothing if it already exists.

For scratch work that should be cleaned up when the job ends, create a temp directory instead:

```kotlin
val tmp = Fs.mktemp()
// use tmp as an absolute path
Fs.copy("build/output.zip", "$tmp/output.zip")
```

### Copying and Moving Files

```kotlin
Fs.copy("target/myapp.jar", "dist/lib/myapp.jar")
Fs.move("build/report.html", "dist/report.html")
```

Both calls automatically create any missing parent directories at the destination.

### Deleting Files

```kotlin
Fs.delete("build/tmp")
```

Deletes the path recursively if it is a directory. Does nothing if the path does not exist.

### Checking Paths

```kotlin
if (!Fs.exists("dist/bin/myapp")) {
    fail("Launcher script was not copied")
}
val isDir = Fs.isDirectory("dist")
val isFile = Fs.isFile("dist/lib/myapp.jar")
```

### Changing Permissions

```kotlin
Fs.chmod("dist/bin/myapp", "755")
```

### Reading and Writing Files

Write a string to a file (overwrites if it already exists):

```kotlin
Fs.write("dist/version.txt", version)
```

Append to a file:

```kotlin
Fs.write("dist/build.log", "Build completed at $timestamp\n", append = true)
```

Read a file back as a string:

```kotlin
val properties = Fs.readAsString("config/build.properties")
```

## Finding Files with Glob

`Fs.glob` finds files matching an ant-style pattern and returns their paths relative to the base directory.

Pattern syntax:
- `*` matches any characters except `/`
- `**` matches any characters including `/`
- `?` matches exactly one character

Find all JARs in a target directory:

```kotlin
val jars = Fs.glob("target/*.jar")
```

Find all Java sources under `src`:

```kotlin
val sources = Fs.glob("src/**.java")
```

Exclude a subtree using the named-parameter form:

```kotlin
val xmlFiles = Fs.glob(
    includes = listOf("**.xml"),
    excludes = listOf("target/**")
)
```

Search relative to a different base directory:

```kotlin
val moduleJars = Fs.glob("**/target/*.jar", baseDir = "modules")
```

Find subdirectories instead of files:

```kotlin
val modules = Fs.glob("*", baseDir = "modules", kind = FsKind.Folder)
```

## Packaging Archives with `Compressor`

`Compressor` creates and extracts archives. The format is detected automatically from the file extension.

Supported formats: `.tar.gz` / `.tgz`, `.tar.xz` / `.txz`, `.tar.bz2` / `.tbz2`, `.tar`, `.zip`, `.gz` (single file only).

### Creating an Archive

Pass a list of files or directories to include:

```kotlin
Compressor.compress("myapp-1.0.tar.gz", listOf("dist/"))
```

Use `Fs.glob` to select specific files:

```kotlin
val reports = Fs.glob("build/reports/**/*.html")
Compressor.compress("reports.zip", reports)
```

### Extracting an Archive

```kotlin
Compressor.extract("downloads/tools.tar.gz", "tools/")
```

Use `stripComponents` to peel off leading path components, the same as `tar --strip-components`. This is useful when archives contain a top-level versioned directory:

```kotlin
// archive contains myapp-1.0/bin/myapp, myapp-1.0/lib/...
// stripComponents = 1 drops the myapp-1.0/ prefix
Compressor.extract("myapp-1.0.tar.gz", "dist/", stripComponents = 1)
```
