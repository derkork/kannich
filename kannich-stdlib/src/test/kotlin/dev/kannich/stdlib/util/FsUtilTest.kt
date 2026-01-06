package dev.kannich.stdlib.util

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FsUtilTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var root: Path

    @BeforeEach
    fun setup() {
        root = tempDir.toAbsolutePath()
    }

    @Test
    fun `test mkdir`() {
        val dirPath = root.resolve("new/deep/dir")
        FsUtil.mkdir(dirPath).getOrThrow()
        assertTrue(Files.exists(dirPath))
        assertTrue(Files.isDirectory(dirPath))
    }

    @Test
    fun `test mktemp`() {
        val path = FsUtil.mktemp("kannich-test").getOrThrow()
        try {
            assertTrue(Files.exists(path))
            assertTrue(Files.isDirectory(path))
            assertTrue(path.fileName.toString().startsWith("kannich-test"))
            assertTrue(path.isAbsolute)
        } finally {
            FsUtil.delete(path).getOrThrow()
        }
    }

    @Test
    fun `test copy file`() {
        val srcFile = root.resolve("source.txt")
        Files.write(srcFile, "hello world".toByteArray())

        val destFile = root.resolve("dest.txt")
        FsUtil.copy(srcFile, destFile).getOrThrow()

        assertTrue(Files.exists(destFile))
        assertEquals("hello world", String(Files.readAllBytes(destFile)))
    }

    @Test
    fun `test copy file to missing directory`() {
        val srcFile = root.resolve("source.txt")
        Files.write(srcFile, "hello world".toByteArray())

        val destFile = root.resolve("missing/dir/dest.txt")
        FsUtil.copy(srcFile, destFile).getOrThrow()

        assertTrue(Files.exists(destFile))
        assertEquals("hello world", String(Files.readAllBytes(destFile)))
    }

    @Test
    fun `test copy directory recursively to missing destination`() {
        val srcDir = root.resolve("srcDir2")
        Files.createDirectory(srcDir)
        Files.write(srcDir.resolve("file1.txt"), "content 1".toByteArray())

        val destDir = root.resolve("missing/destDir")
        FsUtil.copy(srcDir, destDir).getOrThrow()

        assertTrue(Files.exists(destDir.resolve("file1.txt")))
        assertEquals("content 1", String(Files.readAllBytes(destDir.resolve("file1.txt"))))
    }

    @Test
    fun `test move file`() {
        val srcFile = root.resolve("source.txt")
        Files.write(srcFile, "hello world".toByteArray())

        val destFile = root.resolve("moved.txt")
        FsUtil.move(srcFile, destFile).getOrThrow()

        assertTrue(Files.exists(destFile))
        assertEquals("hello world", String(Files.readAllBytes(destFile)))
        assertFalse(Files.exists(srcFile))
    }

    @Test
    fun `test move file to missing directory`() {
        val srcFile = root.resolve("source.txt")
        Files.write(srcFile, "hello world".toByteArray())

        val destFile = root.resolve("missing/dir/moved.txt")
        FsUtil.move(srcFile, destFile).getOrThrow()

        assertTrue(Files.exists(destFile))
        assertEquals("hello world", String(Files.readAllBytes(destFile)))
        assertFalse(Files.exists(srcFile))
    }

    @Test
    fun `test move directory`() {
        val srcDir = root.resolve("srcDirMove")
        Files.createDirectory(srcDir)
        Files.write(srcDir.resolve("file1.txt"), "content 1".toByteArray())

        val destDir = root.resolve("movedDir")
        FsUtil.move(srcDir, destDir).getOrThrow()

        assertTrue(Files.exists(destDir.resolve("file1.txt")))
        assertEquals("content 1", String(Files.readAllBytes(destDir.resolve("file1.txt"))))
        assertFalse(Files.exists(srcDir))
    }

    @Test
    fun `test simple glob`() {
        createGlobTestData()
        val result = FsUtil.glob("src/main/kotlin/*.kt", root).getOrThrow()
        assertEquals(setOf("src/main/kotlin/A.kt", "src/main/kotlin/B.kt"), result.toSet())
    }

    @Test
    fun `test recursive glob`() {
        createGlobTestData()
        val result = FsUtil.glob("**/*.kt", root).getOrThrow()
        assertEquals(setOf("src/main/kotlin/A.kt", "src/main/kotlin/B.kt", "src/test/kotlin/ATest.kt"), result.toSet())
    }

    @Test
    fun `test multiple includes`() {
        createGlobTestData()
        val result = FsUtil.glob(listOf("src/main/kotlin/A.kt", "target/*.jar"), emptyList(), root).getOrThrow()
        assertEquals(setOf("src/main/kotlin/A.kt", "target/app.jar"), result.toSet())
    }

    @Test
    fun `test excludes`() {
        createGlobTestData()
        val result = FsUtil.glob(includes = listOf("**/*.kt"), excludes = listOf("**/ATest.kt"), root).getOrThrow()
        assertEquals(setOf("src/main/kotlin/A.kt", "src/main/kotlin/B.kt"), result.toSet())
    }

    @Test
    fun `test glob with baseDir`() {
        createGlobTestData()
        val baseDir = root.resolve("src/main")
        val result = FsUtil.glob(includes = listOf("kotlin/*.kt"), rootPath = baseDir).getOrThrow()
        assertEquals(setOf("kotlin/A.kt", "kotlin/B.kt"), result.toSet())
    }

    @Test
    fun `test literal path`() {
        createGlobTestData()
        val result = FsUtil.glob("README.md", root).getOrThrow()
        assertEquals(listOf("README.md"), result)
    }

    @Test
    fun `test glob kind file`() {
        createGlobTestData()
        val result = FsUtil.glob("src/*", root, FsKind.File).getOrThrow()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test glob kind folder`() {
        createGlobTestData()
        val result = FsUtil.glob("src/*", root, FsKind.Folder).getOrThrow()
        assertEquals(setOf("src/main", "src/test"), result.toSet())
    }

    @Test
    fun `test glob kind all`() {
        createGlobTestData()
        val result = FsUtil.glob("src/*", root, FsKind.All).getOrThrow()
        assertEquals(setOf("src/main", "src/test"), result.toSet())
    }

    @Test
    fun `test literal folder with kind file`() {
        createGlobTestData()
        val result = FsUtil.glob("src/main", root, FsKind.File).getOrThrow()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test literal folder with kind folder`() {
        createGlobTestData()
        val result = FsUtil.glob("src/main", root, FsKind.Folder).getOrThrow()
        assertEquals(listOf("src/main"), result)
    }

    @Test
    fun `test delete file`() {
        val file = root.resolve("toDelete.txt")
        Files.write(file, "delete me".toByteArray())
        assertTrue(Files.exists(file))

        FsUtil.delete(file).getOrThrow()
        assertFalse(Files.exists(file))
    }

    @Test
    fun `test delete directory recursively`() {
        val dir = root.resolve("toDeleteDir")
        Files.createDirectory(dir)
        Files.write(dir.resolve("file1.txt"), "content".toByteArray())
        val subDir = dir.resolve("sub")
        Files.createDirectory(subDir)
        Files.write(subDir.resolve("file2.txt"), "content".toByteArray())

        assertTrue(Files.exists(dir))

        FsUtil.delete(dir).getOrThrow()
        assertFalse(Files.exists(dir))
    }

    @Test
    fun `test exists`() {
        val file = root.resolve("exists.txt")
        Files.write(file, "content".toByteArray())
        val dir = root.resolve("existsDir")
        Files.createDirectory(dir)

        assertTrue(FsUtil.exists(root.resolve("exists.txt")).getOrThrow())
        assertTrue(FsUtil.exists(root.resolve("existsDir")).getOrThrow())
        assertFalse(FsUtil.exists(root.resolve("nonexistent.txt")).getOrThrow())
    }

    @Test
    fun `test isDirectory`() {
        val file = root.resolve("file.txt")
        Files.write(file, "content".toByteArray())
        val dir = root.resolve("dir")
        Files.createDirectory(dir)

        assertTrue(FsUtil.isDirectory(root.resolve("dir")).getOrThrow())
        assertFalse(FsUtil.isDirectory(root.resolve("file.txt")).getOrThrow())
    }

    @Test
    fun `test isFile`() {
        val file = root.resolve("file.txt")
        Files.write(file, "content".toByteArray())
        val dir = root.resolve("dir")
        Files.createDirectory(dir)

        assertTrue(FsUtil.isFile(root.resolve("file.txt")).getOrThrow())
        assertFalse(FsUtil.isFile(root.resolve("dir")).getOrThrow())
    }

    @Test
    fun `test write inputstream`() {
        val content = "hello world".byteInputStream()
        val target = root.resolve("test.txt")
        FsUtil.write(target, content).getOrThrow()

        assertTrue(Files.exists(target))
        assertEquals("hello world", String(Files.readAllBytes(target)))
    }

    @Test
    fun `test write string smoke`() {
        val target = root.resolve("smoke.txt")
        FsUtil.write(target, "smoke test").getOrThrow()
        assertTrue(Files.exists(target))
        assertEquals("smoke test", String(Files.readAllBytes(target)))
    }

    @Test
    fun `test absolute path verification`() {
        assertTrue(FsUtil.mkdir(Path.of("relative")).isFailure)
        assertTrue(FsUtil.copy(Path.of("rel1"), root).isFailure)
        assertTrue(FsUtil.copy(root, Path.of("rel2")).isFailure)
        assertTrue(FsUtil.move(Path.of("rel1"), root).isFailure)
        assertTrue(FsUtil.delete(Path.of("relative")).isFailure)
        assertTrue(FsUtil.exists(Path.of("relative")).isFailure)
        assertTrue(FsUtil.isDirectory(Path.of("relative")).isFailure)
        assertTrue(FsUtil.isFile(Path.of("relative")).isFailure)
        assertTrue(FsUtil.write(Path.of("relative"), "content").isFailure)
        assertTrue(FsUtil.glob("*.kt", Path.of("relative")).isFailure)

        val exception = FsUtil.mkdir(Path.of("relative")).exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
    }

    private fun createGlobTestData() {
        createFile("src/main/kotlin/A.kt")
        createFile("src/main/kotlin/B.kt")
        createFile("src/main/resources/config.xml")
        createFile("src/test/kotlin/ATest.kt")
        createFile("target/app.jar")
        createFile("target/lib/util.jar")
        createFile("README.md")
    }

    private fun createFile(path: String) {
        val file = root.resolve(path)
        Files.createDirectories(file.parent)
        Files.write(file, "content".toByteArray())
    }
}
