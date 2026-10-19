package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.process.ProcessPort
import com.stacklens.application.port.out.process.ProcessResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class AgentContextHelpersCoverageTest {
    @TempDir
    lateinit var tempDir: Path

    private val root: File by lazy { tempDir.toFile().canonicalFile }

    private val fileSystem = JavaFileSystemAdapter()

    private fun file(
        name: String,
        content: String,
    ): File {
        val created = File(root, name)
        created.parentFile?.mkdirs()
        created.writeText(content)
        return created
    }

    private fun processPort(stdout: String): ProcessPort {
        val port = mock<ProcessPort>()
        whenever(port.resolveGitBinary()).thenReturn("/usr/bin/git")
        whenever(port.run(any(), any(), any())).thenReturn(ProcessResult(0, stdout, ""))
        return port
    }

    @Test
    fun `readFilesAsContext renders every readable file with its language fence`() {
        file("src/a.php", "<?php echo 1;")
        file("noext", "plain")

        val context = AgentContextHelpers.readFilesAsContext(listOf("src/a.php", "noext"), root, fileSystem, fileSystem)

        assertTrue(context.contains("### src/a.php"))
        assertTrue(context.contains("```php"))
        assertTrue(context.contains("```txt"))
        assertTrue(context.contains("plain"))
    }

    @Test
    fun `readFilesAsContext skips escapes missing files and directories`() {
        file("inside.php", "ok")
        File(root, "adir").mkdir()

        val context =
            AgentContextHelpers.readFilesAsContext(
                listOf("../outside", "missing.php", "adir", "bad\u0000name"),
                root,
                fileSystem,
                fileSystem,
            )

        assertEquals("", context)
    }

    @Test
    fun `readFilesAsContext survives a repository root that cannot be canonicalised`() {
        val context = AgentContextHelpers.readFilesAsContext(listOf("src/a.php"), File("root\u0000dir"), fileSystem, fileSystem)

        assertEquals("", context)
    }

    @Test
    fun `extractSniffAffectedPaths keeps only files below the root`() {
        file("src/kept.php", "ok")
        val sniff =
            """
            FILE: ${File(root, "src/kept.php").canonicalPath}
            FILE: ${File(root, "src/kept.php").canonicalPath} (source: standard)
            FILE: ${File("outside.php").absolutePath}
            FILE: bad\u0000name
            summary line without marker
            """.trimIndent()

        assertEquals(listOf("src/kept.php"), AgentContextHelpers.extractSniffAffectedPaths(sniff, root, fileSystem))
    }

    @Test
    fun `extractSniffAffectedPaths survives a root that cannot be canonicalised`() {
        val paths = AgentContextHelpers.extractSniffAffectedPaths("FILE: ${File(root, "src/kept.php").canonicalPath}", File("root\u0000dir"), fileSystem)

        assertEquals(emptyList<String>(), paths)
    }

    @Test
    fun `gitDiffPaths compares against the base sha when one is provided`() {
        val port = processPort("a.php\n\nb.yaml\nc.yml\nd.xml\ne.json\nf.txt\n" + (1..20).joinToString("\n") { "g$it.php" })
        val sha = "0".repeat(40)

        val paths = (AgentContextHelpers.gitDiffPaths(sha, root, port) as AgentContextHelpers.GitDiffResult.Success).paths

        verify(port).run(listOf("/usr/bin/git", "diff", "--name-only", sha, "HEAD"), 10L, root)
        assertEquals(15, paths.size)
        assertTrue(paths.containsAll(listOf("a.php", "b.yaml", "c.yml", "d.xml", "e.json")))
        assertTrue(paths.none { it == "f.txt" })
    }

    @Test
    fun `gitDiffPaths falls back to the working tree for a malformed base sha`() {
        val port = processPort("only.json\n")

        val result = AgentContextHelpers.gitDiffPaths("short", root, port)

        verify(port).run(listOf("/usr/bin/git", "diff", "--name-only", "HEAD"), 10L, root)
        assertEquals(listOf("only.json"), (result as AgentContextHelpers.GitDiffResult.Success).paths)
    }

    @Test
    fun `gitDiffPaths reports a failure when git fails`() {
        val port = mock<ProcessPort>()
        whenever(port.resolveGitBinary()).thenReturn("/usr/bin/git")
        whenever(port.run(any(), any(), any())).thenThrow(IllegalStateException("git missing"))

        val result = AgentContextHelpers.gitDiffPaths(null, root, port)

        assertEquals("git missing", (result as AgentContextHelpers.GitDiffResult.Failure).message)
    }

    @Test
    fun `gitDiffPaths reports the exception type when git fails without a message`() {
        val port = mock<ProcessPort>()
        whenever(port.resolveGitBinary()).thenThrow(IllegalStateException())

        val result = AgentContextHelpers.gitDiffPaths(null, root, port)

        assertEquals("IllegalStateException", (result as AgentContextHelpers.GitDiffResult.Failure).message)
    }

    @Test
    fun `gitDiffPaths tolerates a null base sha`() {
        val port = processPort("")

        val result = AgentContextHelpers.gitDiffPaths(null, root, port)

        assertEquals(emptyList<String>(), (result as AgentContextHelpers.GitDiffResult.Success).paths)
    }
}
