package com.stacklens.application.service.dockertest

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataFileEntry
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataReadResult
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataWriteResult
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.out.file.FileListingReadWriteDeletePort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException
import java.nio.file.Files

class DockerDemoDataFileServiceTest {
    private val adapter = JavaFileSystemAdapter()
    private val service = DockerDemoDataFileService(adapter)

    @TempDir
    lateinit var tempDir: File

    private fun composeIn(dir: File): String = File(dir, "docker-compose.yml").apply { writeText("services: {}\n") }.absolutePath

    private fun labels(composeFile: String?): List<String> = service.listDataFiles(composeFile).map { it.label }

    @Test
    fun `listDataFiles returns empty when no compose file is known`() {
        assertEquals(emptyList<DockerDemoDataFileEntry>(), service.listDataFiles(null))
    }

    @Test
    fun `listDataFiles returns empty when the compose file has no parent directory`() {
        assertEquals(emptyList<DockerDemoDataFileEntry>(), service.listDataFiles("docker-compose.yml"))
        assertEquals(emptyList<DockerDemoDataFileEntry>(), service.listDataFiles(File.separator))
    }

    @Test
    fun `listDataFiles returns empty when the base directory cannot be resolved`() {
        assertEquals(emptyList<DockerDemoDataFileEntry>(), service.listDataFiles("bad\u0000dir/docker-compose.yml"))
    }

    @Test
    fun `listDataFiles returns empty when the base directory cannot be listed`() {
        val composeFile = File(File(tempDir, "missing"), "docker-compose.yml").absolutePath

        assertEquals(emptyList<DockerDemoDataFileEntry>(), service.listDataFiles(composeFile))
    }

    @Test
    fun `listDataFiles lists editable files sorted and labelled relative to the base directory`() {
        File(tempDir, "b.json").writeText("{}")
        File(tempDir, "a.py").writeText("x")
        File(tempDir, "notes.txt").writeText("x")
        File(tempDir, "sub").mkdirs()
        File(tempDir, "sub/c.sql").writeText("x")

        val entries = service.listDataFiles(composeIn(tempDir))

        assertEquals(listOf("a.py", "b.json", "sub/c.sql"), entries.map { it.label })
        assertEquals(
            listOf(
                File(tempDir, "a.py").canonicalPath,
                File(tempDir, "b.json").canonicalPath,
                File(tempDir, "sub/c.sql").canonicalPath,
            ),
            entries.map { it.path },
        )
    }

    @Test
    fun `listDataFiles keeps listing when the compose file cannot be canonicalized`() {
        File(tempDir, "a.py").writeText("x")
        val compose = File(tempDir, "bad\u0000compose.yml").absolutePath

        val labels = labels(compose)

        assertEquals(listOf("a.py"), labels)
    }

    @Test
    fun `listDataFiles skips the well-known noise directories`() {
        listOf("wheels", "__pycache__", ".git", "node_modules").forEach { noise ->
            File(tempDir, "$noise/a.py").also { it.parentFile.mkdirs() }.writeText("x")
        }
        File(tempDir, "seed.py").writeText("x")

        assertEquals(listOf("seed.py"), labels(composeIn(tempDir)))
    }

    @Test
    fun `listDataFiles honours the maximum depth`() {
        File(tempDir, "a/b/c/d/e/ok.py").also { it.parentFile.mkdirs() }.writeText("x")
        File(tempDir, "a/b/c/d/e/f/too-deep.py").also { it.parentFile.mkdirs() }.writeText("x")

        assertEquals(listOf("a/b/c/d/e/ok.py"), labels(composeIn(tempDir)))
    }

    @Test
    fun `listDataFiles skips entries that are neither files nor directories`() {
        val baseDir = File(tempDir, "base").apply { mkdirs() }
        Files.createSymbolicLink(File(baseDir, "dangling.py").toPath(), File(baseDir, "nowhere.py").toPath())

        assertEquals(emptyList<DockerDemoDataFileEntry>(), service.listDataFiles(composeIn(baseDir)))
    }

    @Test
    fun `readDataFile reports a missing compose file`() {
        assertEquals(DockerDemoDataReadResult.ComposeNotFound, service.readDataFile(null, "seed.py"))
    }

    @Test
    fun `readDataFile rejects paths outside the base directory`() {
        val baseDir = File(tempDir, "base").apply { mkdirs() }

        assertEquals(DockerDemoDataReadResult.PathOutsideBase, service.readDataFile(composeIn(baseDir), "/etc/passwd"))
    }

    @Test
    fun `readDataFile refuses the base directory and missing files`() {
        val baseDir = File(tempDir, "base").apply { mkdirs() }
        val composeFile = composeIn(baseDir)

        assertEquals(DockerDemoDataReadResult.NotAFile, service.readDataFile(composeFile, baseDir.absolutePath))
        assertEquals(DockerDemoDataReadResult.NotAFile, service.readDataFile(composeFile, File(baseDir, "gone.py").absolutePath))
    }

    @Test
    fun `readDataFile rejects a non editable extension`() {
        val baseDir = File(tempDir, "base").apply { mkdirs() }
        val notes = File(baseDir, "notes.txt").apply { writeText("read me") }

        assertEquals(DockerDemoDataReadResult.ExtensionNotEditable, service.readDataFile(composeIn(baseDir), notes.absolutePath))
    }

    @Test
    fun `readDataFile reads an editable file case insensitively`() {
        val baseDir = File(tempDir, "base").apply { mkdirs() }
        val seed = File(baseDir, "seed.SQL").apply { writeText("select 1") }

        assertEquals(DockerDemoDataReadResult.Read("select 1"), service.readDataFile(composeIn(baseDir), seed.absolutePath))
    }

    @Test
    fun `writeDataFile reports a missing compose file`() {
        assertEquals(DockerDemoDataWriteResult.ComposeNotFound, service.writeDataFile(null, "seed.py", "x"))
    }

    @Test
    fun `writeDataFile rejects paths outside the base directory`() {
        val baseDir = File(tempDir, "base").apply { mkdirs() }

        assertEquals(
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT),
            service.writeDataFile(composeIn(baseDir), "/tmp/evil.py", "x"),
        )
    }

    @Test
    fun `writeDataFile rejects a bare filename that resolves outside the base directory`() {
        val baseDir = File(tempDir, "base").apply { mkdirs() }

        assertEquals(
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT),
            service.writeDataFile(composeIn(baseDir), "missing.py", "x"),
        )
    }

    @Test
    fun `writeDataFile refuses missing targets the base directory and directories`() {
        val baseDir = File(tempDir, "base").apply { mkdirs() }
        val composeFile = composeIn(baseDir)
        File(baseDir, "a-directory.py").mkdirs()

        assertEquals(
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.TARGET_NOT_A_FILE),
            service.writeDataFile(composeFile, File(baseDir, "gone.py").absolutePath, "x"),
        )
        assertEquals(
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.TARGET_NOT_A_FILE),
            service.writeDataFile(composeFile, baseDir.absolutePath, "x"),
        )
        assertEquals(
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.TARGET_NOT_A_FILE),
            service.writeDataFile(composeFile, File(baseDir, "a-directory.py").absolutePath, "x"),
        )
    }

    @Test
    fun `writeDataFile rejects a non editable extension`() {
        val baseDir = File(tempDir, "base").apply { mkdirs() }
        val notes = File(baseDir, "notes.txt").apply { writeText("read me") }

        assertEquals(
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.EXTENSION_NOT_EDITABLE),
            service.writeDataFile(composeIn(baseDir), notes.absolutePath, "x"),
        )
    }

    @Test
    fun `writeDataFile overwrites an editable file and sanitizes the name`() {
        val baseDir = File(tempDir, "base").apply { mkdirs() }
        val sanitized = File(baseDir, "my_config_.sql").apply { writeText("old") }

        val result = service.writeDataFile(composeIn(baseDir), File(baseDir, "my config!.sql").absolutePath, "new")

        assertEquals(DockerDemoDataWriteResult.Saved(sanitized.canonicalPath), result)
        assertEquals("new", sanitized.readText())
    }

    @Test
    fun `writeDataFile reports a failed write`() {
        val baseDir = File(tempDir, "base").apply { mkdirs() }
        val seed = File(baseDir, "seed.py").apply { writeText("old") }
        val failing = DockerDemoDataFileService(FailingWritePort(adapter))

        assertEquals(
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED),
            failing.writeDataFile(composeIn(baseDir), seed.absolutePath, "new"),
        )
    }

    @Test
    fun `writeDataFile reports a target that cannot be canonicalized`() {
        val baseDir = File(tempDir, "base").apply { mkdirs() }
        val seed = File(baseDir, "seed.py").apply { writeText("old") }
        val port = mock<FileListingReadWriteDeletePort>()
        whenever(port.canonicalFile(any())).thenReturn(baseDir).thenThrow(IllegalStateException("broken link"))

        assertEquals(
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT),
            DockerDemoDataFileService(port).writeDataFile(File(baseDir, "docker-compose.yml").absolutePath, seed.absolutePath, "x"),
        )
    }

    @Test
    fun `writeDataFile reports a base directory that cannot be canonicalized`() {
        val baseDir = File(tempDir, "base").apply { mkdirs() }
        val seed = File(baseDir, "seed.py").apply { writeText("old") }
        val port = mock<FileListingReadWriteDeletePort>()
        whenever(port.canonicalFile(any())).thenReturn(baseDir).thenReturn(seed).thenThrow(IllegalStateException("broken link"))

        assertEquals(
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT),
            DockerDemoDataFileService(port).writeDataFile(File(baseDir, "docker-compose.yml").absolutePath, seed.absolutePath, "x"),
        )
    }

    private class FailingWritePort(
        delegate: FileListingReadWriteDeletePort,
    ) : FileListingReadWriteDeletePort by delegate {
        override fun writeFile(
            file: File,
            content: String,
        ): Unit = throw IOException("write failed")
    }
}
