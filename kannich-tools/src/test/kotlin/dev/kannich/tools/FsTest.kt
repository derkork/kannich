package dev.kannich.tools

import dev.kannich.stdlib.FsKind
import dev.kannich.stdlib.JobContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class FsTest : FunSpec({

    lateinit var tempDir: Path
    lateinit var root: File

    beforeEach {
        tempDir = Files.createTempDirectory("fs-test").toAbsolutePath()
        root = tempDir.toFile()
    }

    afterEach {
        tempDir.let { if (Files.exists(it)) it.toFile().deleteRecursively() }
    }

    suspend fun <T> withJobContext(workingDir: String, block: suspend () -> T): T {
        return withContext(JobContext(workingDir = workingDir)) {
            block()
        }
    }

    fun createFile(path: String) {
        val file = File(root, path)
        file.parentFile.mkdirs()
        file.writeText("content")
    }

    fun createGlobTestData() {
        createFile("src/main/kotlin/A.kt")
        createFile("src/main/kotlin/B.kt")
        createFile("src/main/resources/config.xml")
        createFile("src/test/kotlin/ATest.kt")
        createFile("target/app.jar")
        createFile("target/lib/util.jar")
        createFile("README.md")
    }

    test("test mkdir") {
        withJobContext(tempDir.toString()) {
            val dirPath = "new/deep/dir"
            Fs.mkdir(dirPath)
            val dir = File(tempDir.toFile(), dirPath)
            dir.exists() shouldBe true
            dir.isDirectory shouldBe true
        }
    }

    test("test mktemp") {
        withJobContext(tempDir.toString()) {
            val path = Fs.mktemp("kannich-test")
            val file = File(path)
            try {
                file.exists() shouldBe true
                file.isDirectory shouldBe true
                file.name.startsWith("kannich-test") shouldBe true
            } finally {
                Fs.delete(path)
            }
        }
    }

    test("test copy file") {
        withJobContext(tempDir.toString()) {
            val srcFile = File(tempDir.toFile(), "source.txt")
            srcFile.writeText("hello world")

            val destFile = File(tempDir.toFile(), "dest.txt")
            Fs.copy("source.txt", "dest.txt")

            destFile.exists() shouldBe true
            destFile.readText() shouldBe "hello world"
        }
    }

    test("test copy file to missing directory") {
        withJobContext(tempDir.toString()) {
            val srcFile = File(tempDir.toFile(), "source.txt")
            srcFile.writeText("hello world")

            val destFile = File(tempDir.toFile(), "missing/dir/dest.txt")
            Fs.copy("source.txt", "missing/dir/dest.txt")

            destFile.exists() shouldBe true
            destFile.readText() shouldBe "hello world"
        }
    }

    test("test copy directory recursively to missing destination") {
        withJobContext(tempDir.toString()) {
            val srcDir = File(tempDir.toFile(), "srcDir2")
            srcDir.mkdir()
            File(srcDir, "file1.txt").writeText("content 1")

            val destDir = File(tempDir.toFile(), "missing/destDir")
            Fs.copy("srcDir2", "missing/destDir")

            File(destDir, "file1.txt").exists() shouldBe true
            File(destDir, "file1.txt").readText() shouldBe "content 1"
        }
    }

    test("test move file") {
        withJobContext(tempDir.toString()) {
            val srcFile = File(tempDir.toFile(), "source.txt")
            srcFile.writeText("hello world")

            val destFile = File(tempDir.toFile(), "moved.txt")
            Fs.move("source.txt", "moved.txt")

            destFile.exists() shouldBe true
            destFile.readText() shouldBe "hello world"
            srcFile.exists() shouldBe false
        }
    }

    test("test move file to missing directory") {
        withJobContext(tempDir.toString()) {
            val srcFile = File(tempDir.toFile(), "source.txt")
            srcFile.writeText("hello world")

            val destFile = File(tempDir.toFile(), "missing/dir/moved.txt")
            Fs.move("source.txt", "missing/dir/moved.txt")

            destFile.exists() shouldBe true
            destFile.readText() shouldBe "hello world"
            srcFile.exists() shouldBe false
        }
    }

    test("test move directory") {
        withJobContext(tempDir.toString()) {
            val srcDir = File(tempDir.toFile(), "srcDirMove")
            srcDir.mkdir()
            File(srcDir, "file1.txt").writeText("content 1")

            val destDir = File(tempDir.toFile(), "movedDir")
            Fs.move("srcDirMove", "movedDir")

            File(destDir, "file1.txt").exists() shouldBe true
            File(destDir, "file1.txt").readText() shouldBe "content 1"
            srcDir.exists() shouldBe false
        }
    }

    test("test simple glob") {
        withJobContext(root.absolutePath) {
            createGlobTestData()
            val result = Fs.glob("src/main/kotlin/*.kt")
            result.toSet() shouldContainExactlyInAnyOrder setOf("src/main/kotlin/A.kt", "src/main/kotlin/B.kt")
        }
    }

    test("test recursive glob") {
        withJobContext(root.absolutePath) {
            createGlobTestData()
            val result = Fs.glob("**/*.kt")
            result.toSet() shouldContainExactlyInAnyOrder setOf("src/main/kotlin/A.kt", "src/main/kotlin/B.kt", "src/test/kotlin/ATest.kt")
        }
    }

    test("test multiple includes") {
        withJobContext(root.absolutePath) {
            createGlobTestData()
            val result = Fs.glob(listOf("src/main/kotlin/A.kt", "target/*.jar"))
            result.toSet() shouldContainExactlyInAnyOrder setOf("src/main/kotlin/A.kt", "target/app.jar")
        }
    }

    test("test excludes") {
        withJobContext(root.absolutePath) {
            createGlobTestData()
            val result = Fs.glob(includes = listOf("**/*.kt"), excludes = listOf("**/ATest.kt"))
            result.toSet() shouldContainExactlyInAnyOrder setOf("src/main/kotlin/A.kt", "src/main/kotlin/B.kt")
        }
    }

    test("test glob with baseDir") {
        withJobContext(root.absolutePath) {
            createGlobTestData()
            val result = Fs.glob(includes = listOf("kotlin/*.kt"), baseDir = File(root, "src/main").absolutePath)
            result.toSet() shouldContainExactlyInAnyOrder setOf("kotlin/A.kt", "kotlin/B.kt")
        }
    }

    test("test literal path") {
        withJobContext(root.absolutePath) {
            createGlobTestData()
            val result = Fs.glob("README.md")
            result shouldBe listOf("README.md")
        }
    }

    test("test literal path not exists") {
        withJobContext(root.absolutePath) {
            createGlobTestData()
            val result = Fs.glob("NON_EXISTENT.md")
            result.isEmpty() shouldBe true
        }
    }

    test("test matches directories too") {
        withJobContext(root.absolutePath) {
            createGlobTestData()
            val result = Fs.glob("src/*", kind = FsKind.All)
            result.toSet() shouldContainExactlyInAnyOrder setOf("src/main", "src/test")
        }
    }

    test("test glob kind folder") {
        withJobContext(root.absolutePath) {
            createGlobTestData()
            val result = Fs.glob("src/*", kind = FsKind.Folder)
            result.toSet() shouldContainExactlyInAnyOrder setOf("src/main", "src/test")
        }
    }

    test("test glob kind file") {
        withJobContext(root.absolutePath) {
            createGlobTestData()
            val result = Fs.glob("src/*", kind = FsKind.File)
            result.isEmpty() shouldBe true
        }
    }

    test("test delete file") {
        withJobContext(tempDir.toString()) {
            val file = File(tempDir.toFile(), "toDelete.txt")
            file.writeText("delete me")
            file.exists() shouldBe true

            Fs.delete("toDelete.txt")
            file.exists() shouldBe false
        }
    }

    test("test delete directory recursively") {
        withJobContext(tempDir.toString()) {
            val dir = File(tempDir.toFile(), "toDeleteDir")
            dir.mkdir()
            File(dir, "file1.txt").writeText("content")
            val subDir = File(dir, "sub")
            subDir.mkdir()
            File(subDir, "file2.txt").writeText("content")

            dir.exists() shouldBe true

            Fs.delete("toDeleteDir")
            dir.exists() shouldBe false
        }
    }

    test("test delete non existent") {
        withJobContext(tempDir.toString()) {
            // Should not throw exception
            Fs.delete("nonExistent")
        }
    }

    test("test exists") {
        withJobContext(tempDir.toString()) {
            val file = File(root, "exists.txt")
            file.writeText("content")
            val dir = File(root, "existsDir")
            dir.mkdir()

            Fs.exists("exists.txt") shouldBe true
            Fs.exists("existsDir") shouldBe true
            Fs.exists("nonexistent.txt") shouldBe false
        }
    }

    test("test isDirectory") {
        withJobContext(tempDir.toString()) {
            val file = File(root, "file.txt")
            file.writeText("content")
            val dir = File(root, "dir")
            dir.mkdir()

            Fs.isDirectory("dir") shouldBe true
            Fs.isDirectory("file.txt") shouldBe false
            Fs.isDirectory("nonexistent") shouldBe false
        }
    }

    test("test isFile") {
        withJobContext(tempDir.toString()) {
            val file = File(root, "file.txt")
            file.writeText("content")
            val dir = File(root, "dir")
            dir.mkdir()

            Fs.isFile("file.txt") shouldBe true
            Fs.isFile("dir") shouldBe false
            Fs.isFile("nonexistent") shouldBe false
        }
    }

    test("test write inputstream") {
        withJobContext(tempDir.toString()) {
            val content = "hello world".byteInputStream()
            Fs.write("test.txt", content)

            val file = File(tempDir.toFile(), "test.txt")
            file.exists() shouldBe true
            file.readText() shouldBe "hello world"
        }
    }

    test("test write inputstream to missing directory") {
        withJobContext(tempDir.toString()) {
            val content = "nested content".byteInputStream()
            Fs.write("nested/dir/test.txt", content)

            val file = File(tempDir.toFile(), "nested/dir/test.txt")
            file.exists() shouldBe true
            file.readText() shouldBe "nested content"
        }
    }

    test("test write inputstream append") {
        withJobContext(tempDir.toString()) {
            val file = File(tempDir.toFile(), "append.txt")
            file.writeText("first\n")

            val content = "second".byteInputStream()
            Fs.write("append.txt", content, append = true)

            file.readText() shouldBe "first\nsecond"
        }
    }

    test("test write string smoke") {
        withJobContext(tempDir.toString()) {
            Fs.write("smoke.txt", "smoke test")
            val file = File(tempDir.toFile(), "smoke.txt")
            file.exists() shouldBe true
            file.readText() shouldBe "smoke test"
        }
    }
})
