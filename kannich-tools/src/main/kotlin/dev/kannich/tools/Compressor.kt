package dev.kannich.tools

import dev.kannich.stdlib.FsKind
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
     * - .gz - gzip (single file, extracts in place; stripComponents > 0 not supported)
     *
     * @param archive The path to the archive file
     * @param dest The destination directory to extract to
     * @param stripComponents Number of leading path components to strip, like tar's --strip-components
     */
    suspend fun extract(archive: String, dest: String, stripComponents: Int = 0) {
        logger.debug("Extracting $archive to $dest")
        Fs.mkdir(dest)

        val format = ArchiveFormat.detect(archive)
            ?: fail("Unsupported archive format: $archive")

        if (format == ArchiveFormat.GZ && stripComponents > 0) {
            fail("stripComponents > 0 is not supported for .gz archives")
        }

        val result = when (format) {
            ArchiveFormat.ZIP -> if (stripComponents == 0) {
                Shell.exec("unzip", "-q", "-o", archive, "-d", dest)
            } else {
                // Extract to a temp dir, then move the contents up stripComponents levels
                val tmp = "$dest.strip.tmp"
                try {
                    val unzipResult = Shell.exec("unzip", "-q", "-o", archive, "-d", tmp)
                    if (!unzipResult.success) {
                        fail("Failed to extract $archive: ${unzipResult.stderr}")
                    }
                    val stripped = resolveStripped(tmp, stripComponents)
                    Fs.move(stripped, dest)
                } finally {
                    Fs.delete(tmp)
                }
                return
            }
            ArchiveFormat.GZ -> Shell.execShell("cp '$archive' '$dest/' && gunzip -f '$dest/${archive.substringAfterLast('/')}'")
            else -> Shell.exec("tar", "x${format.tarFlag}f", archive, "-C", dest, "--strip-components=$stripComponents")
        }

        if (!result.success) {
            fail("Failed to extract $archive: ${result.stderr}")
        }
    }

    private suspend fun resolveStripped(base: String, levels: Int): String {
        var current = base
        repeat(levels) {
            val children = Fs.glob("*", baseDir = current, kind = FsKind.Folder)
            if (children.size != 1) {
                fail("Cannot strip components: expected exactly one entry at $current, found ${children.size}")
            }
            current = "$current/${children.first()}"
        }
        return current
    }

    /**
     * Creates an archive from the given paths.
     * Automatically selects the archive tool from the file extension of [archive].
     *
     * Supported formats: .tar.gz/.tgz, .tar.xz/.txz, .tar.bz2/.tbz2, .tar, .zip, .gz
     *
     * Paths are passed to the archiver as-is, relative to the current working directory.
     * Use [Fs.glob] to acquire a list of files to archive.
     *
     * @param archive The path to the archive file to create
     * @param paths The files to include
     * @throws dev.kannich.stdlib.JobFailedException if archiving fails, the format is unsupported,
     *         or .gz is used with a number of paths other than one
     */
    suspend fun compress(archive: String, paths: List<String>) {
        logger.debug("Compressing ${paths.size} path(s) into $archive")

        val format = ArchiveFormat.detect(archive)
            ?: fail("Unsupported archive format: $archive")

        if (format == ArchiveFormat.GZ && paths.size != 1) {
            fail("GZ format requires exactly one input file, got ${paths.size}")
        }

        val result = when (format) {
            ArchiveFormat.ZIP -> Shell.exec("zip", "-r", archive, *paths.toTypedArray())
            ArchiveFormat.GZ -> Shell.execShell("gzip -c '${paths.first()}' > '$archive'")
            else -> Shell.exec("tar", "c${format.tarFlag}f", archive, *paths.toTypedArray())
        }

        if (!result.success) {
            fail("Failed to create archive $archive: ${result.stderr}")
        }
    }

}
