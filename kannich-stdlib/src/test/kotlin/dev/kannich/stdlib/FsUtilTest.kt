package dev.kannich.stdlib

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

class FsUtilTest : FunSpec({

    lateinit var root: Path

    beforeEach {
        root = Files.createTempDirectory("fsutil-test").toAbsolutePath()
    }

    afterEach {
        root.let { paths -> if (Files.exists(paths)) paths.toFile().deleteRecursively() }
    }

    fun createFile(path: String) {
        val file = root.resolve(path)
        Files.createDirectories(file.parent)
        Files.write(file, "content".toByteArray())
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
        val dirPath = root.resolve("new/deep/dir")
        FsUtil.mkdir(dirPath).getOrThrow()
        Files.exists(dirPath) shouldBe true
        Files.isDirectory(dirPath) shouldBe true
    }

    test("test mktemp") {
        val path = FsUtil.mktemp("kannich-test").getOrThrow()
        try {
            Files.exists(path) shouldBe true
            Files.isDirectory(path) shouldBe true
            path.fileName.toString().startsWith("kannich-test") shouldBe true
            path.isAbsolute shouldBe true
        } finally {
            FsUtil.delete(path).getOrThrow()
        }
    }

    test("test copy file") {
        val srcFile = root.resolve("source.txt")
        Files.write(srcFile, "hello world".toByteArray())

        val destFile = root.resolve("dest.txt")
        FsUtil.copy(srcFile, destFile).getOrThrow()

        Files.exists(destFile) shouldBe true
        String(Files.readAllBytes(destFile)) shouldBe "hello world"
    }

    test("test copy file to missing directory") {
        val srcFile = root.resolve("source.txt")
        Files.write(srcFile, "hello world".toByteArray())

        val destFile = root.resolve("missing/dir/dest.txt")
        FsUtil.copy(srcFile, destFile).getOrThrow()

        Files.exists(destFile) shouldBe true
        String(Files.readAllBytes(destFile)) shouldBe "hello world"
    }

    test("test copy directory recursively to missing destination") {
        val srcDir = root.resolve("srcDir2")
        Files.createDirectory(srcDir)
        Files.write(srcDir.resolve("file1.txt"), "content 1".toByteArray())

        val destDir = root.resolve("missing/destDir")
        FsUtil.copy(srcDir, destDir).getOrThrow()

        Files.exists(destDir.resolve("file1.txt")) shouldBe true
        String(Files.readAllBytes(destDir.resolve("file1.txt"))) shouldBe "content 1"
    }

    test("test move file") {
        val srcFile = root.resolve("source.txt")
        Files.write(srcFile, "hello world".toByteArray())

        val destFile = root.resolve("moved.txt")
        FsUtil.move(srcFile, destFile).getOrThrow()

        Files.exists(destFile) shouldBe true
        String(Files.readAllBytes(destFile)) shouldBe "hello world"
        Files.exists(srcFile) shouldBe false
    }

    test("test move file to missing directory") {
        val srcFile = root.resolve("source.txt")
        Files.write(srcFile, "hello world".toByteArray())

        val destFile = root.resolve("missing/dir/moved.txt")
        FsUtil.move(srcFile, destFile).getOrThrow()

        Files.exists(destFile) shouldBe true
        String(Files.readAllBytes(destFile)) shouldBe "hello world"
        Files.exists(srcFile) shouldBe false
    }

    test("test move directory") {
        val srcDir = root.resolve("srcDirMove")
        Files.createDirectory(srcDir)
        Files.write(srcDir.resolve("file1.txt"), "content 1".toByteArray())

        val destDir = root.resolve("movedDir")
        FsUtil.move(srcDir, destDir).getOrThrow()

        Files.exists(destDir.resolve("file1.txt")) shouldBe true
        String(Files.readAllBytes(destDir.resolve("file1.txt"))) shouldBe "content 1"
        Files.exists(srcDir) shouldBe false
    }

    test("test simple glob") {
        createGlobTestData()
        val result = FsUtil.glob("src/main/kotlin/*.kt", root).getOrThrow()
        result.toSet() shouldContainExactlyInAnyOrder setOf("src/main/kotlin/A.kt", "src/main/kotlin/B.kt")
    }

    test("test recursive glob") {
        createGlobTestData()
        val result = FsUtil.glob("**/*.kt", root).getOrThrow()
        result.toSet() shouldContainExactlyInAnyOrder setOf("src/main/kotlin/A.kt", "src/main/kotlin/B.kt", "src/test/kotlin/ATest.kt")
    }

    test("test multiple includes") {
        createGlobTestData()
        val result = FsUtil.glob(listOf("src/main/kotlin/A.kt", "target/*.jar"), emptyList(), root).getOrThrow()
        result.toSet() shouldContainExactlyInAnyOrder setOf("src/main/kotlin/A.kt", "target/app.jar")
    }

    test("test excludes") {
        createGlobTestData()
        val result = FsUtil.glob(includes = listOf("**/*.kt"), excludes = listOf("**/ATest.kt"), root).getOrThrow()
        result.toSet() shouldContainExactlyInAnyOrder setOf("src/main/kotlin/A.kt", "src/main/kotlin/B.kt")
    }

    test("test glob with baseDir") {
        createGlobTestData()
        val baseDir = root.resolve("src/main")
        val result = FsUtil.glob(includes = listOf("kotlin/*.kt"), rootPath = baseDir).getOrThrow()
        result.toSet() shouldContainExactlyInAnyOrder setOf("kotlin/A.kt", "kotlin/B.kt")
    }

    test("test literal path") {
        createGlobTestData()
        val result = FsUtil.glob("README.md", root).getOrThrow()
        result shouldBe listOf("README.md")
    }

    test("test glob kind file") {
        createGlobTestData()
        val result = FsUtil.glob("src/*", root, FsKind.File).getOrThrow()
        result.isEmpty() shouldBe true
    }

    test("test glob kind folder") {
        createGlobTestData()
        val result = FsUtil.glob("src/*", root, FsKind.Folder).getOrThrow()
        result.toSet() shouldContainExactlyInAnyOrder setOf("src/main", "src/test")
    }

    test("test glob kind all") {
        createGlobTestData()
        val result = FsUtil.glob("src/*", root, FsKind.All).getOrThrow()
        result.toSet() shouldContainExactlyInAnyOrder setOf("src/main", "src/test")
    }

    test("test literal folder with kind file") {
        createGlobTestData()
        val result = FsUtil.glob("src/main", root, FsKind.File).getOrThrow()
        result.isEmpty() shouldBe true
    }

    test("test literal folder with kind folder") {
        createGlobTestData()
        val result = FsUtil.glob("src/main", root, FsKind.Folder).getOrThrow()
        result shouldBe listOf("src/main")
    }

    test("test delete file") {
        val file = root.resolve("toDelete.txt")
        Files.write(file, "delete me".toByteArray())
        Files.exists(file) shouldBe true

        FsUtil.delete(file).getOrThrow()
        Files.exists(file) shouldBe false
    }

    test("test delete directory recursively") {
        val dir = root.resolve("toDeleteDir")
        Files.createDirectory(dir)
        Files.write(dir.resolve("file1.txt"), "content".toByteArray())
        val subDir = dir.resolve("sub")
        Files.createDirectory(subDir)
        Files.write(subDir.resolve("file2.txt"), "content".toByteArray())

        Files.exists(dir) shouldBe true

        FsUtil.delete(dir).getOrThrow()
        Files.exists(dir) shouldBe false
    }

    test("test exists") {
        val file = root.resolve("exists.txt")
        Files.write(file, "content".toByteArray())
        val dir = root.resolve("existsDir")
        Files.createDirectory(dir)

        FsUtil.exists(root.resolve("exists.txt")).getOrThrow() shouldBe true
        FsUtil.exists(root.resolve("existsDir")).getOrThrow() shouldBe true
        FsUtil.exists(root.resolve("nonexistent.txt")).getOrThrow() shouldBe false
    }

    test("test isDirectory") {
        val file = root.resolve("file.txt")
        Files.write(file, "content".toByteArray())
        val dir = root.resolve("dir")
        Files.createDirectory(dir)

        FsUtil.isDirectory(root.resolve("dir")).getOrThrow() shouldBe true
        FsUtil.isDirectory(root.resolve("file.txt")).getOrThrow() shouldBe false
    }

    test("test isFile") {
        val file = root.resolve("file.txt")
        Files.write(file, "content".toByteArray())
        val dir = root.resolve("dir")
        Files.createDirectory(dir)

        FsUtil.isFile(root.resolve("file.txt")).getOrThrow() shouldBe true
        FsUtil.isFile(root.resolve("dir")).getOrThrow() shouldBe false
    }

    test("test write inputstream") {
        val content = "hello world".byteInputStream()
        val target = root.resolve("test.txt")
        FsUtil.write(target, content).getOrThrow()

        Files.exists(target) shouldBe true
        String(Files.readAllBytes(target)) shouldBe "hello world"
    }

    test("test write string smoke") {
        val target = root.resolve("smoke.txt")
        FsUtil.write(target, "smoke test").getOrThrow()
        Files.exists(target) shouldBe true
        String(Files.readAllBytes(target)) shouldBe "smoke test"
    }

    test("test absolute path verification") {
        FsUtil.mkdir(Path.of("relative")).isFailure shouldBe true
        FsUtil.copy(Path.of("rel1"), root).isFailure shouldBe true
        FsUtil.copy(root, Path.of("rel2")).isFailure shouldBe true
        FsUtil.move(Path.of("rel1"), root).isFailure shouldBe true
        FsUtil.delete(Path.of("relative")).isFailure shouldBe true
        FsUtil.exists(Path.of("relative")).isFailure shouldBe true
        FsUtil.isDirectory(Path.of("relative")).isFailure shouldBe true
        FsUtil.isFile(Path.of("relative")).isFailure shouldBe true
        FsUtil.write(Path.of("relative"), "content").isFailure shouldBe true
        FsUtil.glob("*.kt", Path.of("relative")).isFailure shouldBe true

        val exception = FsUtil.mkdir(Path.of("relative")).exceptionOrNull()
        (exception is IllegalArgumentException) shouldBe true
    }

    test("test chmod") {
        val file = root.resolve("perm.txt")
        Files.write(file, "content".toByteArray())

        val testCases = listOf(
            "777" to setOf(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE
            ),
            "644" to setOf(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ
            ),
            "755" to setOf(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
            ),
            "512" to setOf(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_WRITE
            ),
            "000" to emptySet()
        )

        for ((perms, expected) in testCases) {
            FsUtil.chmod(file, perms).getOrThrow()
            Files.getPosixFilePermissions(file) shouldBe expected
        }
    }

    test("test chmod invalid format") {
        val file = root.resolve("perm_invalid.txt")
        Files.write(file, "content".toByteArray())

        FsUtil.chmod(file, "77").isFailure shouldBe true
        FsUtil.chmod(file, "7777").isFailure shouldBe true
        FsUtil.chmod(file, "abc").isFailure shouldBe true
        FsUtil.chmod(file, "877").isFailure shouldBe true
        FsUtil.chmod(file, "787").isFailure shouldBe true
        FsUtil.chmod(file, "778").isFailure shouldBe true
    }

    test("test chmod directory") {
        val dir = root.resolve("perm_dir")
        Files.createDirectory(dir)
        FsUtil.chmod(dir, "750").getOrThrow()
        Files.getPosixFilePermissions(dir) shouldBe setOf(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE
        )
    }

    test("test chmod nonexistent path") {
        val nonexistent = root.resolve("nonexistent.txt")
        FsUtil.chmod(nonexistent, "777").isFailure shouldBe true
    }
})
