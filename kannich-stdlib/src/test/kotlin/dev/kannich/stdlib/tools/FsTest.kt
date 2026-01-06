package dev.kannich.stdlib.tools

import dev.kannich.stdlib.TestBase
import dev.kannich.stdlib.util.FsKind
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FsTest : TestBase() {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var root: File

    @BeforeEach
    fun setup() {
        root = tempDir.toFile()
    }

    @Test
    fun `test mkdir`() = withJobContext(tempDir.toString()) {
        val dirPath = "new/deep/dir"
        Fs.mkdir(dirPath)
        val dir = File(tempDir.toFile(), dirPath)
        assertTrue(dir.exists())
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `test mktemp`() = withJobContext(tempDir.toString()) {
        val path = Fs.mktemp("kannich-test")
        val file = File(path)
        try {
            assertTrue(file.exists())
            assertTrue(file.isDirectory)
            assertTrue(file.name.startsWith("kannich-test"))
        } finally {
            Fs.delete(path)
        }
    }

    @Test
    fun `test copy file`() = withJobContext(tempDir.toString()) {
        val srcFile = File(tempDir.toFile(), "source.txt")
        srcFile.writeText("hello world")

        val destFile = File(tempDir.toFile(), "dest.txt")
        Fs.copy("source.txt", "dest.txt")

        assertTrue(destFile.exists())
        assertEquals("hello world", destFile.readText())
    }

    @Test
    fun `test copy file to missing directory`() = withJobContext(tempDir.toString()) {
        val srcFile = File(tempDir.toFile(), "source.txt")
        srcFile.writeText("hello world")

        val destFile = File(tempDir.toFile(), "missing/dir/dest.txt")
        Fs.copy("source.txt", "missing/dir/dest.txt")

        assertTrue(destFile.exists())
        assertEquals("hello world", destFile.readText())
    }

    @Test
    fun `test copy directory recursively to missing destination`() = withJobContext(tempDir.toString()) {
        val srcDir = File(tempDir.toFile(), "srcDir2")
        srcDir.mkdir()
        File(srcDir, "file1.txt").writeText("content 1")

        val destDir = File(tempDir.toFile(), "missing/destDir")
        Fs.copy("srcDir2", "missing/destDir")

        assertTrue(File(destDir, "file1.txt").exists())
        assertEquals("content 1", File(destDir, "file1.txt").readText())
    }

    @Test
    fun `test move file`() = withJobContext(tempDir.toString()) {
        val srcFile = File(tempDir.toFile(), "source.txt")
        srcFile.writeText("hello world")

        val destFile = File(tempDir.toFile(), "moved.txt")
        Fs.move("source.txt", "moved.txt")

        assertTrue(destFile.exists())
        assertEquals("hello world", destFile.readText())
        assertFalse(srcFile.exists())
    }

    @Test
    fun `test move file to missing directory`() = withJobContext(tempDir.toString()) {
        val srcFile = File(tempDir.toFile(), "source.txt")
        srcFile.writeText("hello world")

        val destFile = File(tempDir.toFile(), "missing/dir/moved.txt")
        Fs.move("source.txt", "missing/dir/moved.txt")

        assertTrue(destFile.exists())
        assertEquals("hello world", destFile.readText())
        assertFalse(srcFile.exists())
    }

    @Test
    fun `test move directory`() = withJobContext(tempDir.toString()) {
        val srcDir = File(tempDir.toFile(), "srcDirMove")
        srcDir.mkdir()
        File(srcDir, "file1.txt").writeText("content 1")

        val destDir = File(tempDir.toFile(), "movedDir")
        Fs.move("srcDirMove", "movedDir")

        assertTrue(File(destDir, "file1.txt").exists())
        assertEquals("content 1", File(destDir, "file1.txt").readText())
        assertFalse(srcDir.exists())
    }

    @Test
    fun `test simple glob`() = withJobContext(root.absolutePath) {
        createGlobTestData()
        val result = Fs.glob("src/main/kotlin/*.kt")
        assertEquals(setOf("src/main/kotlin/A.kt", "src/main/kotlin/B.kt"), result.toSet())
    }

    @Test
    fun `test recursive glob`() = withJobContext(root.absolutePath) {
        createGlobTestData()
        val result = Fs.glob("**/*.kt")
        assertEquals(setOf("src/main/kotlin/A.kt", "src/main/kotlin/B.kt", "src/test/kotlin/ATest.kt"), result.toSet())
    }

    @Test
    fun `test multiple includes`() = withJobContext(root.absolutePath) {
        createGlobTestData()
        val result = Fs.glob(listOf("src/main/kotlin/A.kt", "target/*.jar"))
        assertEquals(setOf("src/main/kotlin/A.kt", "target/app.jar"), result.toSet())
    }

    @Test
    fun `test excludes`() = withJobContext(root.absolutePath) {
        createGlobTestData()
        val result = Fs.glob(includes = listOf("**/*.kt"), excludes = listOf("**/ATest.kt"))
        assertEquals(setOf("src/main/kotlin/A.kt", "src/main/kotlin/B.kt"), result.toSet())
    }

    @Test
    fun `test glob with baseDir`() = withJobContext(root.absolutePath) {
        createGlobTestData()
        val result = Fs.glob(includes = listOf("kotlin/*.kt"), baseDir = File(root, "src/main").absolutePath)
        assertEquals(setOf("kotlin/A.kt", "kotlin/B.kt"), result.toSet())
    }

    @Test
    fun `test literal path`() = withJobContext(root.absolutePath) {
        createGlobTestData()
        val result = Fs.glob("README.md")
        assertEquals(listOf("README.md"), result)
    }

    @Test
    fun `test literal path not exists`() = withJobContext(root.absolutePath) {
        createGlobTestData()
        val result = Fs.glob("NON_EXISTENT.md")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test matches directories too`() = withJobContext(root.absolutePath) {
        createGlobTestData()
        val result = Fs.glob("src/*", kind = FsKind.All)
        assertEquals(setOf("src/main", "src/test"), result.toSet())
    }

    @Test
    fun `test glob kind folder`() = withJobContext(root.absolutePath) {
        createGlobTestData()
        val result = Fs.glob("src/*", kind = FsKind.Folder)
        assertEquals(setOf("src/main", "src/test"), result.toSet())
    }

    @Test
    fun `test glob kind file`() = withJobContext(root.absolutePath) {
        createGlobTestData()
        val result = Fs.glob("src/*", kind = FsKind.File)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test delete file`() = withJobContext(tempDir.toString()) {
        val file = File(tempDir.toFile(), "toDelete.txt")
        file.writeText("delete me")
        assertTrue(file.exists())

        Fs.delete("toDelete.txt")
        assertFalse(file.exists())
    }

    @Test
    fun `test delete directory recursively`() = withJobContext(tempDir.toString()) {
        val dir = File(tempDir.toFile(), "toDeleteDir")
        dir.mkdir()
        File(dir, "file1.txt").writeText("content")
        val subDir = File(dir, "sub")
        subDir.mkdir()
        File(subDir, "file2.txt").writeText("content")

        assertTrue(dir.exists())

        Fs.delete("toDeleteDir")
        assertFalse(dir.exists())
    }

    @Test
    fun `test delete non existent`() = withJobContext(tempDir.toString()) {
        // Should not throw exception
        Fs.delete("nonExistent")
    }

    @Test
    fun `test exists`() = withJobContext(tempDir.toString()) {
        val file = File(root, "exists.txt")
        file.writeText("content")
        val dir = File(root, "existsDir")
        dir.mkdir()

        assertTrue(Fs.exists("exists.txt"))
        assertTrue(Fs.exists("existsDir"))
        assertFalse(Fs.exists("nonexistent.txt"))
    }

    @Test
    fun `test isDirectory`() = withJobContext(tempDir.toString()) {
        val file = File(root, "file.txt")
        file.writeText("content")
        val dir = File(root, "dir")
        dir.mkdir()

        assertTrue(Fs.isDirectory("dir"))
        assertFalse(Fs.isDirectory("file.txt"))
        assertFalse(Fs.isDirectory("nonexistent"))
    }

    @Test
    fun `test isFile`() = withJobContext(tempDir.toString()) {
        val file = File(root, "file.txt")
        file.writeText("content")
        val dir = File(root, "dir")
        dir.mkdir()

        assertTrue(Fs.isFile("file.txt"))
        assertFalse(Fs.isFile("dir"))
        assertFalse(Fs.isFile("nonexistent"))
    }

    @Test
    fun `test write inputstream`() = withJobContext(tempDir.toString()) {
        val content = "hello world".byteInputStream()
        Fs.write("test.txt", content)

        val file = File(tempDir.toFile(), "test.txt")
        assertTrue(file.exists())
        assertEquals("hello world", file.readText())
    }

    @Test
    fun `test write inputstream to missing directory`() = withJobContext(tempDir.toString()) {
        val content = "nested content".byteInputStream()
        Fs.write("nested/dir/test.txt", content)

        val file = File(tempDir.toFile(), "nested/dir/test.txt")
        assertTrue(file.exists())
        assertEquals("nested content", file.readText())
    }

    @Test
    fun `test write inputstream append`() = withJobContext(tempDir.toString()) {
        val file = File(tempDir.toFile(), "append.txt")
        file.writeText("first\n")

        val content = "second".byteInputStream()
        Fs.write("append.txt", content, append = true)

        assertEquals("first\nsecond", file.readText())
    }

    @Test
    fun `test write string smoke`() = withJobContext(tempDir.toString()) {
        Fs.write("smoke.txt", "smoke test")
        val file = File(tempDir.toFile(), "smoke.txt")
        assertTrue(file.exists())
        assertEquals("smoke test", file.readText())
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
        val file = File(root, path)
        file.parentFile.mkdirs()
        file.writeText("content")
    }

}
