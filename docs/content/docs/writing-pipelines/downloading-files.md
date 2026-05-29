+++
title = "Downloading Files"
weight = 5
+++

## Introduction

This guide covers how to download files from the internet inside a pipeline job using the built-in `Web` tool. `Web` is a thin wrapper around `curl` that handles temporary file management automatically. For cases where you need full control over `curl` flags or want to use another download tool, you can always call it directly via `Shell`.

## Downloading to a Temporary File

`Web.download` fetches a URL and saves the result to a temporary location. The file is deleted automatically when the job finishes, so no cleanup code is needed.

```kotlin
val archive = Web.download("https://example.com/releases/tool-1.2.3.tar.gz")
Compressor.extract(archive, "tool")
```

By default, the filename is derived from the URL. You can override it with the `filename` parameter:

```kotlin
val binary = Web.download("https://example.com/latest", filename = "mytool")
```

## Downloading to a Specific Location

`Web.downloadTo` saves the file to a path you choose. The parent directory is created automatically if it does not exist. The file is still cleaned up when the job ends.

```kotlin
Web.downloadTo("https://example.com/config.yaml", "path/to/config")
```

When the destination is a directory, the filename is derived from the URL (same rules as `Web.download`):

```kotlin
Fs.mkdir("downloads")
Web.downloadTo("https://example.com/releases/tool-1.2.3.tar.gz", "downloads")
// saves to $workingDir/downloads/tool-1.2.3.tar.gz
```

Pass `filename` to override the name when the destination is a directory:

```kotlin
Web.downloadTo("https://example.com/latest", "downloads", filename = "tool")
// saves to downloads/tool
```

## Error Handling

Both `Web.download` and `Web.downloadTo` fail the job automatically if the HTTP request fails or the server returns an error status. You do not need to check the return value. A returned path always points to a successfully downloaded file.

## Using curl Directly

If you need flags that `Web` does not expose - custom headers, client certificates, rate limiting, or anything else - call `curl` through `Shell` directly:

```kotlin
val result = Shell.exec(
    "curl", "-sSLf",
    "-H", "Authorization: Bearer ${requireEnv("API_TOKEN")}",
    "-o", "$workingDir/data.json",
    "https://api.example.com/export"
)
if (!result.success) {
    fail("Export failed: ${result.stderr}")
}
```
