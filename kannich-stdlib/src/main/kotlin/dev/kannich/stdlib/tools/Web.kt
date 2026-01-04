package dev.kannich.stdlib.tools

import dev.kannich.stdlib.currentJobScope
import dev.kannich.stdlib.fail

/**
 * Built-in tool for downloading files.
 */
object Web {

    /**
     * Downloads a file from a URL to a temporary directory. When the download fails, cleans up any leftover files.
     * On success, returns the path to the downloaded file. The file will be automatically deleted when the job ends.
     *
     * @param url The URL to download from
     * @param filename Optional filename. If not provided, derived from URL or uses a generated name.
     * @return The path to the downloaded file
     * @throws dev.kannich.stdlib.JobFailedException if the download fails
     */
    suspend fun download(url: String, filename: String? = null): String {
        // Create temporary directory for download
        val tempDir = Fs.mktemp("download")

        // Cleanup on job end
        currentJobScope().onCleanup { Fs.delete(tempDir) }

        // Determine filename
        val actualFilename = filename
            ?: url.substringAfterLast('/').takeIf { it.isNotBlank() && !it.contains('?') }
            ?: "download-${System.currentTimeMillis()}"

        val outputPath = "$tempDir/$actualFilename"

        // Download with curl
        // -s: silent, -S: show errors, -L: follow redirects, -f: fail on HTTP errors, -o: output file
        val result = Shell.exec("curl", "-sSLf", "-o", outputPath, url)
        if (!result.success) {
            fail("Failed to download $url: ${result.stderr}")
        }

        return outputPath
    }

    /**
     * Downloads a file from a URL to a specific destination. The file will be automatically deleted when the job ends.
     *
     * @param url The URL to download from
     * @param dest The destination path (file path or directory)
     * @param filename Optional filename when dest is a directory
     * @return The path to the downloaded file
     * @throws dev.kannich.stdlib.JobFailedException if the download fails
     */
    suspend fun downloadTo(url: String, dest: String, filename: String? = null): String {
        // Determine the output path
        val outputPath = if (filename != null) {
            "$dest/$filename"
        } else if (Fs.isDirectory(dest)) {
            val name = url.substringAfterLast('/').takeIf { it.isNotBlank() && !it.contains('?') }
                ?: "download-${System.currentTimeMillis()}"
            "$dest/$name"
        } else {
            dest
        }

        // Ensure parent directory exists
        val parentDir = outputPath.substringBeforeLast('/')
        Fs.mkdir(parentDir)

        // Download with curl
        val result = Shell.exec("curl", "-sSLf", "-o", outputPath, url)
        if (!result.success) {
            fail("Failed to download $url: ${result.stderr}")
        }

        // Cleanup when job ends
        currentJobScope().onCleanup { Fs.delete(outputPath) }

        return outputPath
    }
}
