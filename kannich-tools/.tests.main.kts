@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")

import dev.kannich.stdlib.FsKind
import dev.kannich.test.*
import dev.kannich.tools.Apt
import dev.kannich.tools.Compressor
import dev.kannich.tools.Docker
import dev.kannich.tools.Fs
import dev.kannich.tools.Git
import dev.kannich.tools.Gpg
import dev.kannich.tools.Shell

var tempDir = ""

testSuite {
    beforeEach {
        tempDir = Fs.mktemp("fs-test")
    }

    group("apt") {
        test("apt install works") {
            Apt.install("tree")
            val result = Shell.exec("tree", "--version")

            verify(result.success, "Execution failed.")
            verify(result.stdout.contains("tree"), "Expected 'tree' to be installed.")
        }
    }

    group("fs") {
        test("mkdir creates nested directory") {
            val dir = "$tempDir/new/deep/dir"
            Fs.mkdir(dir)
            verify(Fs.exists(dir), "Directory was not created")
            verify(Fs.isDirectory(dir), "Path is not a directory")
        }

        test("mktemp creates prefixed temp directory") {
            val path = Fs.mktemp("kannich-test")
            verify(Fs.exists(path), "Temp directory does not exist")
            verify(Fs.isDirectory(path), "Temp path is not a directory")
            verify(path.contains("kannich-test"), "Temp dir name does not contain prefix")
        }

        test("copy file") {
            Fs.write("$tempDir/source.txt", "hello world")
            Fs.copy("$tempDir/source.txt", "$tempDir/dest.txt")
            verify(Fs.exists("$tempDir/dest.txt"), "Destination file does not exist")
            verify(Fs.readAsString("$tempDir/dest.txt") == "hello world", "Content mismatch")
        }

        test("copy file creates missing destination directories") {
            Fs.write("$tempDir/source.txt", "hello world")
            Fs.copy("$tempDir/source.txt", "$tempDir/missing/dir/dest.txt")
            verify(Fs.exists("$tempDir/missing/dir/dest.txt"), "Destination file does not exist")
            verify(Fs.readAsString("$tempDir/missing/dir/dest.txt") == "hello world", "Content mismatch")
        }

        test("copy directory recursively to missing destination") {
            Fs.mkdir("$tempDir/srcDir")
            Fs.write("$tempDir/srcDir/file1.txt", "content 1")
            Fs.copy("$tempDir/srcDir", "$tempDir/missing/destDir")
            verify(Fs.exists("$tempDir/missing/destDir/file1.txt"), "Copied file does not exist")
            verify(Fs.readAsString("$tempDir/missing/destDir/file1.txt") == "content 1", "Content mismatch")
        }

        test("move file") {
            Fs.write("$tempDir/source.txt", "hello world")
            Fs.move("$tempDir/source.txt", "$tempDir/moved.txt")
            verify(Fs.exists("$tempDir/moved.txt"), "Moved file does not exist")
            verify(Fs.readAsString("$tempDir/moved.txt") == "hello world", "Content mismatch")
            verify(!Fs.exists("$tempDir/source.txt"), "Source file still exists after move")
        }

        test("move file creates missing destination directories") {
            Fs.write("$tempDir/source.txt", "hello world")
            Fs.move("$tempDir/source.txt", "$tempDir/missing/dir/moved.txt")
            verify(Fs.exists("$tempDir/missing/dir/moved.txt"), "Moved file does not exist")
            verify(!Fs.exists("$tempDir/source.txt"), "Source file still exists after move")
        }

        test("move directory") {
            Fs.mkdir("$tempDir/srcDir")
            Fs.write("$tempDir/srcDir/file1.txt", "content 1")
            Fs.move("$tempDir/srcDir", "$tempDir/movedDir")
            verify(Fs.exists("$tempDir/movedDir/file1.txt"), "File does not exist in moved directory")
            verify(!Fs.exists("$tempDir/srcDir"), "Source directory still exists after move")
        }

        test("delete file") {
            Fs.write("$tempDir/toDelete.txt", "delete me")
            verify(Fs.exists("$tempDir/toDelete.txt"), "File does not exist before delete")
            Fs.delete("$tempDir/toDelete.txt")
            verify(!Fs.exists("$tempDir/toDelete.txt"), "File still exists after delete")
        }

        test("delete directory recursively") {
            Fs.mkdir("$tempDir/toDeleteDir/sub")
            Fs.write("$tempDir/toDeleteDir/file1.txt", "content")
            Fs.write("$tempDir/toDeleteDir/sub/file2.txt", "content")
            Fs.delete("$tempDir/toDeleteDir")
            verify(!Fs.exists("$tempDir/toDeleteDir"), "Directory still exists after delete")
        }

        test("delete non-existent path does not throw") {
            Fs.delete("$tempDir/nonExistent")
        }

        test("exists returns true for file and directory, false for missing") {
            Fs.write("$tempDir/exists.txt", "content")
            Fs.mkdir("$tempDir/existsDir")
            verify(Fs.exists("$tempDir/exists.txt"), "File should exist")
            verify(Fs.exists("$tempDir/existsDir"), "Directory should exist")
            verify(!Fs.exists("$tempDir/nonexistent.txt"), "Non-existent path should not exist")
        }

        test("isDirectory distinguishes directories from files") {
            Fs.write("$tempDir/file.txt", "content")
            Fs.mkdir("$tempDir/dir")
            verify(Fs.isDirectory("$tempDir/dir"), "Should be a directory")
            verify(!Fs.isDirectory("$tempDir/file.txt"), "File should not be a directory")
            verify(!Fs.isDirectory("$tempDir/nonexistent"), "Non-existent should not be a directory")
        }

        test("isFile distinguishes files from directories") {
            Fs.write("$tempDir/file.txt", "content")
            Fs.mkdir("$tempDir/dir")
            verify(Fs.isFile("$tempDir/file.txt"), "Should be a file")
            verify(!Fs.isFile("$tempDir/dir"), "Directory should not be a file")
            verify(!Fs.isFile("$tempDir/nonexistent"), "Non-existent should not be a file")
        }

        test("write string creates file with content") {
            Fs.write("$tempDir/smoke.txt", "smoke test")
            verify(Fs.exists("$tempDir/smoke.txt"), "File does not exist")
            verify(Fs.readAsString("$tempDir/smoke.txt") == "smoke test", "Content mismatch")
        }

        test("write string creates missing parent directories") {
            Fs.write("$tempDir/nested/dir/test.txt", "nested content")
            verify(Fs.exists("$tempDir/nested/dir/test.txt"), "File does not exist")
            verify(Fs.readAsString("$tempDir/nested/dir/test.txt") == "nested content", "Content mismatch")
        }

        test("write string appends when append is true") {
            Fs.write("$tempDir/append.txt", "first\n")
            Fs.write("$tempDir/append.txt", "second", append = true)
            verify(Fs.readAsString("$tempDir/append.txt") == "first\nsecond", "Appended content mismatch")
        }

        test("write inputstream creates file") {
            val content = "hello world".byteInputStream()
            Fs.write("$tempDir/test.txt", content)
            verify(Fs.exists("$tempDir/test.txt"), "File does not exist")
            verify(Fs.readAsString("$tempDir/test.txt") == "hello world", "Content mismatch")
        }

        test("write inputstream appends when append is true") {
            Fs.write("$tempDir/append.txt", "first\n")
            val content = "second".byteInputStream()
            Fs.write("$tempDir/append.txt", content, append = true)
            verify(Fs.readAsString("$tempDir/append.txt") == "first\nsecond", "Appended content mismatch")
        }

        test("glob matches files by pattern") {
            Fs.write("$tempDir/src/main/kotlin/A.kt", "")
            Fs.write("$tempDir/src/main/kotlin/B.kt", "")
            Fs.write("$tempDir/src/main/resources/config.xml", "")
            val result = Fs.glob("*.kt", baseDir = "$tempDir/src/main/kotlin")
            verify(result.toSet() == setOf("A.kt", "B.kt"), "Glob result mismatch: $result")
        }

        test("glob matches files recursively") {
            Fs.write("$tempDir/src/main/kotlin/A.kt", "")
            Fs.write("$tempDir/src/main/kotlin/B.kt", "")
            Fs.write("$tempDir/src/test/kotlin/ATest.kt", "")
            Fs.write("$tempDir/target/app.jar", "")
            val result = Fs.glob("**/*.kt", baseDir = tempDir)
            verify(
                result.toSet() == setOf("src/main/kotlin/A.kt", "src/main/kotlin/B.kt", "src/test/kotlin/ATest.kt"),
                "Glob result mismatch: $result"
            )
        }

        test("glob supports excludes") {
            Fs.write("$tempDir/src/main/kotlin/A.kt", "")
            Fs.write("$tempDir/src/main/kotlin/B.kt", "")
            Fs.write("$tempDir/src/test/kotlin/ATest.kt", "")
            val result = Fs.glob(includes = listOf("**/*.kt"), excludes = listOf("**/ATest.kt"), baseDir = tempDir)
            verify(
                result.toSet() == setOf("src/main/kotlin/A.kt", "src/main/kotlin/B.kt"),
                "Glob result mismatch: $result"
            )
        }

        test("glob literal path returns file when it exists") {
            Fs.write("$tempDir/README.md", "")
            val result = Fs.glob("README.md", baseDir = tempDir)
            verify(result == listOf("README.md"), "Glob result mismatch: $result")
        }

        test("glob literal path returns empty when file does not exist") {
            val result = Fs.glob("NON_EXISTENT.md", baseDir = tempDir)
            verify(result.isEmpty(), "Expected empty result but got: $result")
        }

        test("glob with FsKind.Folder returns only directories") {
            Fs.write("$tempDir/src/main/kotlin/A.kt", "")
            Fs.write("$tempDir/src/test/kotlin/ATest.kt", "")
            val result = Fs.glob("src/*", baseDir = tempDir, kind = FsKind.Folder)
            verify(result.toSet() == setOf("src/main", "src/test"), "Glob result mismatch: $result")
        }

        test("glob with FsKind.File returns only files") {
            Fs.write("$tempDir/src/main/kotlin/A.kt", "")
            val result = Fs.glob("src/*", baseDir = tempDir, kind = FsKind.File)
            verify(result.isEmpty(), "Expected no files at src/* but got: $result")
        }
    }

    group("compressor") {
        // For tar-based formats, absolute paths are stored without the leading slash.
        // Helper to compute expected extraction path from an absolute source path.
        fun extractedPath(destDir: String, absoluteSrcPath: String) =
            "$destDir/${absoluteSrcPath.removePrefix("/")}"

        test("compress and extract tar.gz") {
            val src = "$tempDir/hello.txt"
            Fs.write(src, "hello tar.gz")
            val archive = "$tempDir/test.tar.gz"
            val destDir = Fs.mktemp("extract-test")
            Compressor.compress(archive, listOf(src))
            Compressor.extract(archive, destDir)
            val extracted = extractedPath(destDir, src)
            verify(Fs.exists(extracted), "Extracted file does not exist: $extracted")
            verify(Fs.readAsString(extracted) == "hello tar.gz", "Content mismatch")
        }

        test("compress and extract .tgz") {
            val src = "$tempDir/hello.txt"
            Fs.write(src, "hello tgz")
            val archive = "$tempDir/test.tgz"
            val destDir = Fs.mktemp("extract-test")
            Compressor.compress(archive, listOf(src))
            Compressor.extract(archive, destDir)
            val extracted = extractedPath(destDir, src)
            verify(Fs.exists(extracted), "Extracted file does not exist: $extracted")
            verify(Fs.readAsString(extracted) == "hello tgz", "Content mismatch")
        }

        test("compress and extract tar.xz") {
            val src = "$tempDir/hello.txt"
            Fs.write(src, "hello tar.xz")
            val archive = "$tempDir/test.tar.xz"
            val destDir = Fs.mktemp("extract-test")
            Compressor.compress(archive, listOf(src))
            Compressor.extract(archive, destDir)
            val extracted = extractedPath(destDir, src)
            verify(Fs.exists(extracted), "Extracted file does not exist: $extracted")
            verify(Fs.readAsString(extracted) == "hello tar.xz", "Content mismatch")
        }

        test("compress and extract tar.bz2") {
            val src = "$tempDir/hello.txt"
            Fs.write(src, "hello tar.bz2")
            val archive = "$tempDir/test.tar.bz2"
            val destDir = Fs.mktemp("extract-test")
            Compressor.compress(archive, listOf(src))
            Compressor.extract(archive, destDir)
            val extracted = extractedPath(destDir, src)
            verify(Fs.exists(extracted), "Extracted file does not exist: $extracted")
            verify(Fs.readAsString(extracted) == "hello tar.bz2", "Content mismatch")
        }

        test("compress and extract .tar") {
            val src = "$tempDir/hello.txt"
            Fs.write(src, "hello tar")
            val archive = "$tempDir/test.tar"
            val destDir = Fs.mktemp("extract-test")
            Compressor.compress(archive, listOf(src))
            Compressor.extract(archive, destDir)
            val extracted = extractedPath(destDir, src)
            verify(Fs.exists(extracted), "Extracted file does not exist: $extracted")
            verify(Fs.readAsString(extracted) == "hello tar", "Content mismatch")
        }

        test("compress and extract .zip") {
            val src = "$tempDir/hello.txt"
            Fs.write(src, "hello zip")
            val archive = "$tempDir/test.zip"
            val destDir = Fs.mktemp("extract-test")
            Compressor.compress(archive, listOf(src))
            Compressor.extract(archive, destDir)
            val extracted = extractedPath(destDir, src)
            verify(Fs.exists(extracted), "Extracted file does not exist: $extracted")
            verify(Fs.readAsString(extracted) == "hello zip", "Content mismatch")
        }

        test("compress and extract .gz") {
            val src = "$tempDir/hello.txt"
            Fs.write(src, "hello gz")
            val archive = "$tempDir/hello.gz"
            val destDir = Fs.mktemp("extract-test")
            Compressor.compress(archive, listOf(src))
            Compressor.extract(archive, destDir)
            // GZ: archive is copied to dest and gunzipped, filename is archive name minus .gz
            val extracted = "$destDir/hello"
            verify(Fs.exists(extracted), "Extracted file does not exist: $extracted")
            verify(Fs.readAsString(extracted) == "hello gz", "Content mismatch")
        }

        test("tar.gz archive contains multiple files") {
            Fs.write("$tempDir/a.txt", "file a")
            Fs.write("$tempDir/b.txt", "file b")
            val archive = "$tempDir/multi.tar.gz"
            val destDir = Fs.mktemp("extract-test")
            Compressor.compress(archive, listOf("$tempDir/a.txt", "$tempDir/b.txt"))
            Compressor.extract(archive, destDir)
            verify(Fs.readAsString(extractedPath(destDir, "$tempDir/a.txt")) == "file a", "Content mismatch for a.txt")
            verify(Fs.readAsString(extractedPath(destDir, "$tempDir/b.txt")) == "file b", "Content mismatch for b.txt")
        }

        test("compress and extract using relative paths from fs.glob") {
            // Files are created in workspace (not /tmp) so we can cd into them via relative path.
            // This exercises the intended glob → compress workflow: glob returns relative paths,
            // cd sets the CWD so those paths resolve correctly for the archiver.
            val srcDir = "compressor-glob-test-${tempDir.substringAfterLast('/')}"
            Fs.write("$srcDir/a.txt", "file a")
            Fs.write("$srcDir/subdir/b.txt", "file b")

            val files = Fs.glob("**.txt", baseDir = srcDir)
            val archive = "$tempDir/relative.tar.gz"
            val destDir = Fs.mktemp("extract-test")

            cd(srcDir) {
                Compressor.compress(archive, files)
            }
            Fs.delete(srcDir)

            Compressor.extract(archive, destDir)
            verify(Fs.readAsString("$destDir/a.txt") == "file a", "Content mismatch for a.txt")
            verify(Fs.readAsString("$destDir/subdir/b.txt") == "file b", "Content mismatch for subdir/b.txt")
        }

        test("gz compress rejects multiple files") {
            Fs.write("$tempDir/a.txt", "a")
            Fs.write("$tempDir/b.txt", "b")
            verifyFail("GZ format requires exactly one input file") {
                Compressor.compress("$tempDir/out.gz", listOf("$tempDir/a.txt", "$tempDir/b.txt"))
            }
        }

        test("extract tar.gz with stripComponents strips leading directory") {
            val srcDir = "compressor-strip-tar-${tempDir.substringAfterLast('/')}"
            Fs.write("$srcDir/wrapper/hello.txt", "stripped tar content")
            val archive = "$tempDir/strip.tar.gz"
            val destDir = Fs.mktemp("extract-test")
            cd(srcDir) {
                Compressor.compress(archive, listOf("wrapper/hello.txt"))
            }
            Fs.delete(srcDir)
            Compressor.extract(archive, destDir, stripComponents = 1)
            verify(Fs.exists("$destDir/hello.txt"), "Stripped file does not exist at $destDir/hello.txt")
            verify(Fs.readAsString("$destDir/hello.txt") == "stripped tar content", "Content mismatch")
        }

        test("extract zip with stripComponents strips leading directory") {
            val srcDir = "compressor-strip-zip-${tempDir.substringAfterLast('/')}"
            Fs.write("$srcDir/wrapper/hello.txt", "stripped zip content")
            val archive = "$tempDir/strip.zip"
            val destDir = Fs.mktemp("extract-test")
            cd(srcDir) {
                Compressor.compress(archive, listOf("wrapper/hello.txt"))
            }
            Fs.delete(srcDir)
            Compressor.extract(archive, destDir, stripComponents = 1)
            verify(Fs.exists("$destDir/hello.txt"), "Stripped file does not exist at $destDir/hello.txt")
            verify(Fs.readAsString("$destDir/hello.txt") == "stripped zip content", "Content mismatch")
        }

        test("gz extract rejects stripComponents > 0") {
            Fs.write("$tempDir/a.txt", "a")
            val archive = "$tempDir/a.gz"
            Compressor.compress(archive, listOf("$tempDir/a.txt"))
            val destDir = Fs.mktemp("extract-test")
            verifyFail("stripComponents > 0 is not supported for .gz archives") {
                Compressor.extract(archive, destDir, stripComponents = 1)
            }
        }
    }

    group("docker") {
        test("docker hello-world runs successfully") {
            Docker.enable()
            val result = Docker.exec("run", "--rm", "hello-world")
            verify(result.stdout.contains("Hello from Docker!"), "Expected hello-world output in stdout")
        }
    }

    group("git") {
        // Each test needs its own repo in the workspace so cd() can navigate there with a relative path.
        // git config is set locally so global git config is not polluted.

        test("git is available") {
            val result = Git.exec("--version")
            verify(result.stdout.contains("git version"), "Expected git version string in output")
        }

        test("currentCommitSha returns a 40-character hex SHA") {
            val repoDir = "git-test-sha-${tempDir.substringAfterLast('/')}"
            Fs.write("$repoDir/file.txt", "content")
            cd(repoDir) {
                Git.exec("init")
                Git.exec("config", "--local", "user.email", "test@kannich.dev")
                Git.exec("config", "--local", "user.name", "Kannich Test")
                Git.exec("add", ".")
                Git.exec("commit", "-m", "initial commit")
                val sha = Git.currentCommitSha()
                verify(sha.matches(Regex("[0-9a-f]{40}")), "Expected 40-character hex SHA but got: $sha")
            }
            Fs.delete(repoDir)
        }

        test("currentBranches returns the active branch") {
            val repoDir = "git-test-branches-${tempDir.substringAfterLast('/')}"
            Fs.write("$repoDir/file.txt", "content")
            cd(repoDir) {
                Git.exec("init", "-b", "main")
                Git.exec("config", "--local", "user.email", "test@kannich.dev")
                Git.exec("config", "--local", "user.name", "Kannich Test")
                Git.exec("add", ".")
                Git.exec("commit", "-m", "initial commit")
                val branches = Git.currentBranches()
                verify(branches.contains("main"), "Expected 'main' in branches but got: $branches")
            }
            Fs.delete(repoDir)
        }

        test("currentTags returns tags pointing to HEAD") {
            val repoDir = "git-test-tags-${tempDir.substringAfterLast('/')}"
            Fs.write("$repoDir/file.txt", "content")
            cd(repoDir) {
                Git.exec("init")
                Git.exec("config", "--local", "user.email", "test@kannich.dev")
                Git.exec("config", "--local", "user.name", "Kannich Test")
                Git.exec("add", ".")
                Git.exec("commit", "-m", "initial commit")
                Git.exec("tag", "v1.0.0")
                val tags = Git.currentTags()
                verify(tags.contains("v1.0.0"), "Expected tag 'v1.0.0' but got: $tags")
            }
            Fs.delete(repoDir)
        }
    }

    group("gpg") {
        test("importKey imports a key into the gpg keyring") {
            // Use a unique email per test run to avoid keyring collisions across runs.
            val email = "kannich-test-${tempDir.substringAfterLast('/')}@example.com"
            val keySpec = """
                %no-protection
                Key-Type: RSA
                Key-Length: 1024
                Name-Real: Kannich Test
                Name-Email: $email
                Expire-Date: 0
                %commit
            """.trimIndent()
            Fs.write("$tempDir/keyspec.txt", keySpec)
            Shell.exec("gpg", "--batch", "--gen-key", "$tempDir/keyspec.txt")

            val export = Shell.execShell("gpg --armor --export-secret-keys '$email'")
            verify(export.success && export.stdout.isNotBlank(), "Failed to export generated test key")

            // gpg batch delete requires a fingerprint, not an email.
            val fpr = Shell.execShell("gpg --list-keys --with-colons '$email' | grep '^fpr' | head -1 | cut -d: -f10")
            verify(fpr.success && fpr.stdout.isNotBlank(), "Failed to get key fingerprint")
            val fingerprint = fpr.stdout.trim()

            // Delete from keyring so importKey is exercised on a fresh import.
            Shell.exec("gpg", "--batch", "--yes", "--delete-secret-and-public-key", fingerprint)

            Gpg.importKey(export.stdout)

            val list = Shell.exec("gpg", "--list-keys", email, silent = true)
            verify(list.success, "Expected key '$email' to be present in keyring after importKey")

            // Cleanup
            Shell.exec("gpg", "--batch", "--yes", "--delete-secret-and-public-key", fingerprint)
        }
    }
}
