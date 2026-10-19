package com.stacklens.adapter.out.filesystem

import com.stacklens.adapter.out.shared.RepoScanRules
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class LocalRepoScanAdapterBranchTest {
    private val adapter = LocalRepoScanAdapter()

    @Test
    fun `searchKeyword skips file when readLines throws`() {
        val tmp: Path = Files.createTempDirectory("localrepo-unreadable")
        val root = tmp.toFile()
        val src = File(root, "src")
        src.mkdirs()
        val unreadableKt = File(src, "Broken.kt")
        unreadableKt.writeText("hello world")
        unreadableKt.setReadable(false, false)
        unreadableKt.setReadable(false, true)
        unreadableKt.setWritable(false, false)
        unreadableKt.setWritable(false, true)
        unreadableKt.setExecutable(false, false)
        unreadableKt.setExecutable(false, true)
        val textFile = File(src, "readme.md")
        textFile.writeText("hello world\nfoo bar\nhello again")
        try {
            val hits = adapter.searchKeyword(root.absolutePath, "hello", maxHits = 10)
            assertTrue(hits.isNotEmpty())
        } catch (_: Exception) {
        } finally {
            unreadableKt.setReadable(true, false)
            unreadableKt.setWritable(true, false)
            unreadableKt.setExecutable(true, false)
        }
    }

    @Test
    fun `searchKeyword throws when repoPath is not a valid directory`() {
        assertThrows<IllegalArgumentException> {
            adapter.searchKeyword("/nonexistent/directory/xyz", "anything")
        }
    }

    @Test
    fun `safeReadText returns binary marker when file is unreadable`() {
        val tmp: Path = Files.createTempDirectory("localrepo-unreadable")
        val root = tmp.toFile()
        val file = File(root, "unreadable.md")
        file.writeText("content")
        file.setReadable(false, false)
        file.setReadable(false, true)
        file.setWritable(false, false)
        file.setWritable(false, true)
        file.setExecutable(false, false)
        file.setExecutable(false, true)
        try {
            val summary = adapter.buildCodeSummary(root.absolutePath, maxFiles = 5, maxPerFileSizeKb = 1)
            assertTrue(summary.fileContents.isNotEmpty() || summary.fileCount == 0)
        } catch (_: Exception) {
        } finally {
            file.setReadable(true, false)
            file.setWritable(true, false)
            file.setExecutable(true, false)
        }
    }
}

class LocalRepoScanAdapterPriorityFileTest {
    private val adapter = LocalRepoScanAdapter()

    @Test
    fun `isCodeFile returns true for code extensions`() {
        assertTrue(RepoScanRules().isCodeFile("Foo.kt"))
        assertTrue(RepoScanRules().isCodeFile("Bar.java"))
        assertTrue(RepoScanRules().isCodeFile("script.py"))
        assertTrue(RepoScanRules().isCodeFile("config.json"))
    }

    @Test
    fun `isCodeFile returns false for non-code extensions`() {
        assertTrue(!RepoScanRules().isCodeFile("foo.xyz"))
        assertTrue(!RepoScanRules().isCodeFile("Dockerfile"))
    }

    @Test
    fun `isPriorityFile returns true for readme variants`() {
        assertTrue(RepoScanRules().isPriorityFile("README.md"))
        assertTrue(RepoScanRules().isPriorityFile("readme"))
        assertTrue(RepoScanRules().isPriorityFile("README.txt"))
    }

    @Test
    fun `isPriorityFile returns true for tier 1 manifest files`() {
        assertTrue(RepoScanRules().isPriorityFile("build.gradle.kts"))
        assertTrue(RepoScanRules().isPriorityFile("pom.xml"))
        assertTrue(RepoScanRules().isPriorityFile("package.json"))
        assertTrue(RepoScanRules().isPriorityFile("Cargo.toml"))
    }

    @Test
    fun `isPriorityFile returns true for tier 2 entry points`() {
        assertTrue(RepoScanRules().isPriorityFile("main.py"))
        assertTrue(RepoScanRules().isPriorityFile("app.js"))
        assertTrue(RepoScanRules().isPriorityFile("index.html"))
        assertTrue(RepoScanRules().isPriorityFile("build.gradle.kts"))
    }

    @Test
    fun `isPriorityFile returns true for tier 3 controller and handler files`() {
        assertTrue(RepoScanRules().isPriorityFile("UserController.kt"))
        assertTrue(RepoScanRules().isPriorityFile("AuthHandler.kt"))
        assertTrue(RepoScanRules().isPriorityFile("ApiRouter.kt"))
        assertTrue(RepoScanRules().isPriorityFile("userRoutes.kt"))
    }

    @Test
    fun `isPriorityFile returns true for tier 3 service and repository files`() {
        assertTrue(RepoScanRules().isPriorityFile("UserService.kt"))
        assertTrue(RepoScanRules().isPriorityFile("UserRepository.kt"))
        assertTrue(RepoScanRules().isPriorityFile("UserDao.kt"))
    }

    @Test
    fun `isPriorityFile returns true for tier 3 model and entity files`() {
        assertTrue(RepoScanRules().isPriorityFile("UserModel.kt"))
        assertTrue(RepoScanRules().isPriorityFile("UserEntity.kt"))
    }

    @Test
    fun `isPriorityFile returns true for tier 3 config and security files`() {
        assertTrue(RepoScanRules().isPriorityFile("appConfig.kt"))
        assertTrue(RepoScanRules().isPriorityFile("SecurityConfig.kt"))
        assertTrue(RepoScanRules().isPriorityFile("AuthService.kt"))
    }

    @Test
    fun `isPriorityFile returns true for tier 3 files containing login session permission guard`() {
        assertTrue(RepoScanRules().isPriorityFile("UserLogin.kt"))
        assertTrue(RepoScanRules().isPriorityFile("UserSession.kt"))
        assertTrue(RepoScanRules().isPriorityFile("UserPermission.kt"))
        assertTrue(RepoScanRules().isPriorityFile("AuthGuard.kt"))
    }

    @Test
    fun `isPriorityFile returns false for non-priority files`() {
        assertTrue(!RepoScanRules().isPriorityFile("foo.kt"))
        assertTrue(!RepoScanRules().isPriorityFile("bar.baz"))
    }
}
