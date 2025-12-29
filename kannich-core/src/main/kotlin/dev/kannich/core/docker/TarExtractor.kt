package dev.kannich.core.docker

import java.io.File
import java.io.InputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Extracts tar archives from Docker container copy operations.
 */
object TarExtractor {

    private val logger = LoggerFactory.getLogger(TarExtractor::class.java)


    /**
     * Extracts a tar stream to a destination directory.
     */
    fun extract(tarStream: InputStream, destDir: File) {
        try {
            destDir.mkdirs()
            TarArchiveInputStream(tarStream).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val destFile = File(destDir, entry.name)
                    logger.debug("Extracting ${entry.name}")

                    // Security check: prevent path traversal attacks
                    if (!destFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                        throw SecurityException("Tar entry outside target dir: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        destFile.outputStream().use { out ->
                            tar.copyTo(out)
                        }
                    }

                    entry = tar.nextEntry
                }
            }
        }
        catch (e: IOException) {
            logger.error("Error extracting Tar", e)
        }
    }
}
