package dev.kannich.stdlib.tools

import dev.kannich.stdlib.fail

/**
 * Built-in tool for downloading files.
 * Downloads to a temporary directory by default.
 */
class DownloadTool {
    private val fs = FsTool()
    private val shell = ShellTool()

    /**
     * Downloads a file from a URL to a temporary directory.
     *
     * @param url The URL to download from
     * @param filename Optional filename. If not provided, derived from URL or uses a generated name.
     * @return The path to the downloaded file
     * @throws dev.kannich.stdlib.JobFailedException if the download fails
     */
    fun download(url: String, filename: String? = null): String {
        // Create temp directory for download
        val tempDir = fs.mktemp("download")

        // Determine filename
        val actualFilename = filename
            ?: url.substringAfterLast('/').takeIf { it.isNotBlank() && !it.contains('?') }
            ?: "download-${System.currentTimeMillis()}"

        val outputPath = "$tempDir/$actualFilename"

        // Download with curl
        // -s: silent, -S: show errors, -L: follow redirects, -f: fail on HTTP errors, -o: output file
        val result = shell.exec("curl", "-sSLf", "-o", outputPath, url)
        if (!result.success) {
            fs.delete(tempDir)
            fail("Failed to download $url: ${result.stderr}")
        }

        return outputPath
    }

    /**
     * Downloads a file from a URL to a specific destination.
     *
     * @param url The URL to download from
     * @param dest The destination path (file path or directory)
     * @param filename Optional filename when dest is a directory
     * @return The path to the downloaded file
     * @throws dev.kannich.stdlib.JobFailedException if the download fails
     */
    fun downloadTo(url: String, dest: String, filename: String? = null): String {
        // Determine the output path
        val outputPath = if (filename != null) {
            "$dest/$filename"
        } else if (fs.isDirectory(dest)) {
            val name = url.substringAfterLast('/').takeIf { it.isNotBlank() && !it.contains('?') }
                ?: "download-${System.currentTimeMillis()}"
            "$dest/$name"
        } else {
            dest
        }

        // Ensure parent directory exists
        val parentDir = outputPath.substringBeforeLast('/')
        fs.mkdir(parentDir)

        // Download with curl
        val result = shell.exec("curl", "-sSLf", "-o", outputPath, url)
        if (!result.success) {
            fail("Failed to download $url: ${result.stderr}")
        }

        return outputPath
    }
}
