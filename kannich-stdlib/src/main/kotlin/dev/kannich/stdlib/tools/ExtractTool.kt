package dev.kannich.stdlib.tools

import dev.kannich.stdlib.fail

/**
 * Built-in tool for extracting archives.
 * Automatically detects archive format from file extension.
 */
class ExtractTool {
    private val fs = FsTool()
    private val shell = ShellTool()

    private enum class ArchiveFormat(val extensions: List<String>, val tarFlag: String?) {
        TAR_GZ(listOf(".tar.gz", ".tgz"), "z"),
        TAR_XZ(listOf(".tar.xz", ".txz"), "J"),
        TAR_BZ2(listOf(".tar.bz2", ".tbz2"), "j"),
        TAR(listOf(".tar"), ""),
        ZIP(listOf(".zip"), null),
        GZ(listOf(".gz"), null);

        companion object {
            fun detect(path: String): ArchiveFormat? {
                val lower = path.lowercase()
                return entries.find { format -> format.extensions.any { lower.endsWith(it) } }
            }
        }
    }

    /**
     * Extracts an archive to the specified destination.
     * Automatically detects the archive format from the file extension.
     *
     * Supported formats:
     * - .tar.gz, .tgz - gzipped tar
     * - .tar.xz, .txz - xz-compressed tar
     * - .tar.bz2, .tbz2 - bzip2-compressed tar
     * - .tar - uncompressed tar
     * - .zip - zip archive
     * - .gz - gzip (single file, extracts in place)
     *
     * @param archive The path to the archive file
     * @param dest The destination directory to extract to
     * @throws dev.kannich.stdlib.JobFailedException if extraction fails or format is unsupported
     */
    fun extract(archive: String, dest: String) {
        fs.mkdir(dest)

        val format = ArchiveFormat.detect(archive)
            ?: fail("Unsupported archive format: $archive")

        val result = when (format) {
            ArchiveFormat.ZIP -> shell.exec("unzip", "-q", "-o", archive, "-d", dest)
            ArchiveFormat.GZ -> shell.execShell("cp '$archive' '$dest/' && gunzip -f '$dest/${archive.substringAfterLast('/')}'")
            else -> shell.exec("tar", "x${format.tarFlag}f", archive, "-C", dest)
        }

        if (!result.success) {
            fail("Failed to extract $archive: ${result.stderr}")
        }
    }

    /**
     * Downloads and extracts an archive in one operation.
     * More efficient than download + extract as it pipes directly without intermediate file.
     *
     * @param url The URL to download from
     * @param dest The destination directory to extract to
     * @param format The archive format (tar.gz, tar.xz, tar.bz2, tar, zip). If null, auto-detected from URL.
     * @throws dev.kannich.stdlib.JobFailedException if download or extraction fails
     */
    fun downloadAndExtract(url: String, dest: String, format: String? = null) {
        fs.mkdir(dest)

        val archiveFormat = if (format != null) {
            ArchiveFormat.entries.find { it.extensions.any { ext -> ext.endsWith(format) } }
                ?: fail("Unsupported archive format: $format")
        } else {
            ArchiveFormat.detect(url) ?: ArchiveFormat.TAR_GZ // default for URLs without extensions
        }

        val result = when (archiveFormat) {
            ArchiveFormat.ZIP, ArchiveFormat.GZ -> {
                // zip and gz don't support piping, need to download first
                val tempDir = fs.mktemp("extract")
                val ext = archiveFormat.extensions.first()
                val tempFile = "$tempDir/archive$ext"
                val extractResult = shell.execShell("curl -sSLf -o '$tempFile' '$url'")
                if (!extractResult.success) {
                    fs.delete(tempDir)
                    fail("Failed to download $url: ${extractResult.stderr}")
                }
                extract(tempFile, dest)
                fs.delete(tempDir)
                return
            }
            else -> shell.execShell("curl -sSLf '$url' | tar x${archiveFormat.tarFlag}f - -C '$dest'")
        }

        if (!result.success) {
            fail("Failed to download and extract $url: ${result.stderr}")
        }
    }
}
