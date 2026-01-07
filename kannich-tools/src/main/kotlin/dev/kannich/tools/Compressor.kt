package dev.kannich.tools

import dev.kannich.stdlib.fail
import org.slf4j.LoggerFactory

/**
 * Built-in tool for extracting archives.
 * Automatically detects archive format from file extension.
 */
object Compressor {
    private val logger = LoggerFactory.getLogger(Compressor::class.java)

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
    suspend fun extract(archive: String, dest: String) {
        logger.debug("Extracting $archive to $dest")
        Fs.mkdir(dest)

        val format = ArchiveFormat.detect(archive)
            ?: fail("Unsupported archive format: $archive")

        val result = when (format) {
            ArchiveFormat.ZIP -> Shell.exec("unzip", "-q", "-o", archive, "-d", dest)
            ArchiveFormat.GZ -> Shell.execShell("cp '$archive' '$dest/' && gunzip -f '$dest/${archive.substringAfterLast('/')}'")
            else -> Shell.exec("tar", "x${format.tarFlag}f", archive, "-C", dest)
        }

        if (!result.success) {
            fail("Failed to extract $archive: ${result.stderr}")
        }
    }

}
