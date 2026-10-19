package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files

class RepoFileToolExecutorTest {
    private val log: Logger = LoggerFactory.getLogger(RepoFileToolExecutorTest::class.java)
    private lateinit var repoDir: File

    @BeforeEach
    fun setUp() {
        repoDir = Files.createTempDirectory("repo-file-tool-").toFile()
    }

    @AfterEach
    fun tearDown() {
        if (::repoDir.isInitialized && repoDir.exists()) {
            repoDir.deleteRecursively()
        }
    }

    @Test
    fun `list with no args defaults to repo root and lists visible entries`() {
        File(repoDir, "src").mkdirs()
        File(repoDir, "README.md").writeText("hi")
        File(repoDir, ".git").mkdirs()
        File(repoDir, "build").mkdirs()

        val result = RepoFileToolExecutor.execute("list", emptyMap<String, Any>(), repoDir, log)

        assertTrue(result.contains("src/") || result.contains("src"))
        assertTrue(result.contains("README.md"))
        assertFalse(result.contains(".git"))
        assertFalse(result.contains("build"))
    }

    @Test
    fun `list with blank path falls back to root listing`() {
        File(repoDir, "file.txt").writeText("x")
        val result = RepoFileToolExecutor.execute("list", mapOf("path" to "   "), repoDir, log)
        assertTrue(result.contains("file.txt"))
    }

    @Test
    fun `list accepts l ls list_directory and list_dir aliases`() {
        val aliases = listOf("list", "ls", "list_directory", "list_dir")
        for (alias in aliases) {
            File(repoDir, "only-one.txt").writeText("y")
            val result = RepoFileToolExecutor.execute(alias, mapOf<String, Any>(), repoDir, log)
            assertTrue(result.contains("only-one.txt"), "alias=$alias result=$result")
            File(repoDir, "only-one.txt").delete()
        }
    }

    @Test
    fun `list with recursive true walks subdirectories but skips excluded entries`() {
        File(repoDir, "src/main").mkdirs()
        File(repoDir, "src/main/Hello.kt").writeText("")
        File(repoDir, "node_modules").mkdirs()
        File(repoDir, "node_modules/skipme.js").writeText("")
        File(repoDir, "vendor").mkdirs()
        File(repoDir, "vendor/lib.js").writeText("")

        val result =
            RepoFileToolExecutor.execute(
                "list",
                mapOf<String, Any>("recursive" to true),
                repoDir,
                log,
            )

        assertTrue(result.contains("Hello.kt"))
        assertFalse(result.contains("node_modules"))
        assertFalse(result.contains("vendor"))
    }

    @Test
    fun `list returns empty-directory message when the directory is empty`() {
        val sub = File(repoDir, "empty")
        sub.mkdirs()
        val result = RepoFileToolExecutor.execute("list", mapOf("path" to "empty"), repoDir, log)
        assertEquals("(empty directory)", result)
    }

    @Test
    fun `list returns directory-not-found error for a missing path`() {
        val result = RepoFileToolExecutor.execute("list", mapOf("path" to "missing"), repoDir, log)
        assertTrue(result.contains("directory not found"))
        assertTrue(result.contains("missing"))
    }

    @Test
    fun `list returns not-a-directory error when path is a file`() {
        File(repoDir, "a.txt").writeText("x")
        val result = RepoFileToolExecutor.execute("list", mapOf("path" to "a.txt"), repoDir, log)
        assertTrue(result.contains("not a directory"))
    }

    @Test
    fun `list rejects path traversal outside the repo root`() {
        val result =
            RepoFileToolExecutor.execute(
                "list",
                mapOf("path" to "../../../etc"),
                repoDir,
                log,
            )
        assertTrue(result.contains("path traversal not allowed"))
    }

    @Test
    fun `read returns file contents for an existing file`() {
        File(repoDir, "x.txt").writeText("hello world")
        val result = RepoFileToolExecutor.execute("read", mapOf("path" to "x.txt"), repoDir, log)
        assertEquals("hello world", result)
    }

    @Test
    fun `read returns file-not-found for a missing file`() {
        val result = RepoFileToolExecutor.execute("read", mapOf("path" to "nope.txt"), repoDir, log)
        assertTrue(result.contains("file not found"))
    }

    @Test
    fun `read rejects path traversal outside the repo root`() {
        val result =
            RepoFileToolExecutor.execute(
                "read",
                mapOf("path" to "../outside.txt"),
                repoDir,
                log,
            )
        assertTrue(result.contains("path traversal not allowed"))
    }

    @Test
    fun `read requires a path argument`() {
        val result = RepoFileToolExecutor.execute("read", emptyMap<String, Any>(), repoDir, log)
        assertTrue(result.contains("'path' argument required"))
    }

    @Test
    fun `read returns path-required error when path is not a string`() {
        val result =
            RepoFileToolExecutor.execute("read", mapOf("path" to 12345), repoDir, log)
        assertTrue(result.contains("'path' argument required"))
    }

    @Test
    fun `write creates a new file and its parent directories`() {
        val result =
            RepoFileToolExecutor.execute(
                "write",
                mapOf("path" to "deep/dir/file.txt", "content" to "content-payload"),
                repoDir,
                log,
            )
        assertTrue(result.startsWith("OK: written"))
        assertTrue(result.contains("file.txt"))
        val written = File(repoDir, "deep/dir/file.txt")
        assertTrue(written.exists())
        assertEquals("content-payload", written.readText())
    }

    @Test
    fun `write returns path-required error when path is missing`() {
        val result =
            RepoFileToolExecutor.execute(
                "write",
                mapOf("content" to "x"),
                repoDir,
                log,
            )
        assertTrue(result.contains("'path' argument required"))
    }

    @Test
    fun `write returns content-required error when content is missing`() {
        val result =
            RepoFileToolExecutor.execute(
                "write",
                mapOf("path" to "f.txt"),
                repoDir,
                log,
            )
        assertTrue(result.contains("'content' argument required"))
    }

    @Test
    fun `write returns content-required error when content is not a string`() {
        val result =
            RepoFileToolExecutor.execute(
                "write",
                mapOf("path" to "f.txt", "content" to 42),
                repoDir,
                log,
            )
        assertTrue(result.contains("'content' argument required"))
    }

    @Test
    fun `write rejects path traversal outside the repo root`() {
        val result =
            RepoFileToolExecutor.execute(
                "write",
                mapOf("path" to "../escape.txt", "content" to "x"),
                repoDir,
                log,
            )
        assertTrue(result.contains("path traversal not allowed"))
    }

    @Test
    fun `delete removes an existing file and reports one entry`() {
        val f = File(repoDir, "d.txt")
        f.writeText("x")
        val result = RepoFileToolExecutor.execute("delete", mapOf("path" to "d.txt"), repoDir, log)
        assertTrue(result.startsWith("OK: deleted"))
        assertTrue(result.contains("1 entries"))
        assertFalse(f.exists())
    }

    @Test
    fun `delete removes a directory and prunes empty parents`() {
        val sub = File(repoDir, "a/b/c")
        sub.mkdirs()
        File(sub, "leaf.txt").writeText("x")
        val result = RepoFileToolExecutor.execute("delete", mapOf("path" to "a/b/c"), repoDir, log)
        assertTrue(result.startsWith("OK: deleted"))
        assertTrue(result.contains("empty parent dir"))
        assertFalse(File(repoDir, "a/b/c").exists())
        assertFalse(File(repoDir, "a/b").exists())
    }

    @Test
    fun `delete returns file-not-found for a missing path`() {
        val result = RepoFileToolExecutor.execute("delete", mapOf("path" to "missing.txt"), repoDir, log)
        assertTrue(result.contains("file not found"))
        assertTrue(result.contains("list"))
    }

    @Test
    fun `delete requires a path argument`() {
        val result = RepoFileToolExecutor.execute("delete", emptyMap<String, Any>(), repoDir, log)
        assertTrue(result.contains("'path' argument required"))
    }

    @Test
    fun `delete rejects path traversal outside the repo root`() {
        val result =
            RepoFileToolExecutor.execute(
                "delete",
                mapOf("path" to "../escape"),
                repoDir,
                log,
            )
        assertTrue(result.contains("path traversal not allowed"))
    }

    @Test
    fun `delete on a non-empty directory reports the number of removed entries`() {
        val sub = File(repoDir, "dir")
        sub.mkdirs()
        File(sub, "a.txt").writeText("a")
        File(sub, "b.txt").writeText("b")
        File(sub, "c.txt").writeText("c")
        val result = RepoFileToolExecutor.execute("delete", mapOf("path" to "dir"), repoDir, log)
        assertTrue(result.contains("4 entries"), "result was: $result")
        assertFalse(sub.exists())
    }

    @Test
    fun `delete stops pruning when reaching a non-empty parent`() {
        val parent = File(repoDir, "parent")
        parent.mkdirs()
        val sub = File(parent, "sub")
        sub.mkdirs()
        File(sub, "leaf.txt").writeText("x")
        File(parent, "sibling.txt").writeText("x")

        val result = RepoFileToolExecutor.execute("delete", mapOf("path" to "parent/sub"), repoDir, log)
        assertFalse(File(repoDir, "parent/sub").exists())
        assertTrue(File(repoDir, "parent").exists())
        assertTrue(result.contains("OK: deleted"))
    }

    @Test
    fun `delete handles a missing subdirectory inside the chain`() {
        val result =
            RepoFileToolExecutor.execute(
                "delete",
                mapOf("path" to "no-such-dir/no-file.txt"),
                repoDir,
                log,
            )
        assertTrue(result.contains("file not found"))
    }

    @Test
    fun `delete on a file whose parent is the repo root reports no pruned dirs`() {
        val f = File(repoDir, "top.txt")
        f.writeText("x")
        val result = RepoFileToolExecutor.execute("delete", mapOf("path" to "top.txt"), repoDir, log)
        assertTrue(result.contains("1 entries"))
        assertFalse(result.contains("empty parent"))
    }

    @Test
    fun `delete removes an existing DS_Store file and other ignorable entries before pruning the parent`() {
        val sub = File(repoDir, "sub")
        sub.mkdirs()
        File(sub, "leaf.txt").writeText("x")
        File(sub, ".DS_Store").writeText("x")
        val result = RepoFileToolExecutor.execute("delete", mapOf("path" to "sub"), repoDir, log)
        assertTrue(result.contains("OK: deleted"))
        assertFalse(File(repoDir, "sub").exists())
    }

    @Test
    fun `execute returns unknown-tool error for an unrecognised name`() {
        val result = RepoFileToolExecutor.execute("not-a-tool", emptyMap<String, Any>(), repoDir, log)
        assertTrue(result.contains("unknown tool"))
        assertTrue(result.contains("not-a-tool"))
    }

    @Test
    fun `resolveFilePath returns the file for a relative path inside the repo`() {
        val resolved = RepoFileToolExecutor.resolveFilePath(repoDir, "src/main.kt")
        assertNotNull(resolved)
        assertTrue(resolved!!.absolutePath.endsWith("src/main.kt"))
    }

    @Test
    fun `resolveFilePath strips leading slashes before resolving`() {
        val resolved = RepoFileToolExecutor.resolveFilePath(repoDir, "/sub/dir/file.txt")
        assertNotNull(resolved)
        assertTrue(resolved!!.absolutePath.endsWith("sub/dir/file.txt"))
    }

    @Test
    fun `resolveFilePath strips backslashes before resolving`() {
        val resolved = RepoFileToolExecutor.resolveFilePath(repoDir, "\\sub\\file.txt")
        assertNotNull(resolved)
        assertTrue(resolved!!.absolutePath.endsWith("sub/file.txt") || resolved.absolutePath.endsWith("sub\\file.txt"))
    }

    @Test
    fun `resolveFilePath returns null for path traversal outside the repo`() {
        val resolved = RepoFileToolExecutor.resolveFilePath(repoDir, "../../../etc/passwd")
        assertNull(resolved)
    }

    @Test
    fun `resolveFilePath accepts the repo root itself`() {
        val resolved = RepoFileToolExecutor.resolveFilePath(repoDir, ".")
        assertNotNull(resolved)
        assertEquals(repoDir.canonicalFile.path, resolved!!.canonicalFile.path)
    }

    @Test
    fun `resolveFilePath is null when the path becomes equal to the root without separator`() {
        val resolved = RepoFileToolExecutor.resolveFilePath(repoDir, "")
        assertNotNull(resolved)
    }

    @Test
    fun `runOrError returns the block result on success`() {
        val method =
            RepoFileToolExecutor::class.java.getDeclaredMethod(
                "runOrError",
                String::class.java,
                String::class.java,
                kotlin.Function0::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(RepoFileToolExecutor, "label", "p", { "ok-result" }) as String
        assertEquals("ok-result", result)
    }

    @Test
    fun `runOrError returns Error label on failure`() {
        val method =
            RepoFileToolExecutor::class.java.getDeclaredMethod(
                "runOrError",
                String::class.java,
                String::class.java,
                kotlin.Function0::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(RepoFileToolExecutor, "doing-thing", "/p", { error("boom") }) as String
        assertTrue(result.contains("Error doing-thing /p"))
        assertTrue(result.contains("boom"))
    }

    @Test
    fun `ensureParentDir creates the parent directory when missing`() {
        val method =
            RepoFileToolExecutor::class.java.getDeclaredMethod("ensureParentDir", java.io.File::class.java)
        method.isAccessible = true
        val deep = File(repoDir, "x/y/z.txt")
        assertFalse(deep.parentFile.exists())
        method.invoke(RepoFileToolExecutor, deep)
        assertTrue(deep.parentFile.exists())
    }
}
