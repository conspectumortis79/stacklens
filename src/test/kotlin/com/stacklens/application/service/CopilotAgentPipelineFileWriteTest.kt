package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.pipeline.PipelineWriteErrorCode
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.application.port.out.file.FileSystemPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.whenever
import java.io.File

class CopilotAgentPipelineFileWriteTest : CopilotAgentPipelineServiceTestBase() {
    private fun fileBlock(
        path: String,
        content: String,
    ): String = "### $path\n```kotlin\n$content```"

    private fun runWith(
        svc: CopilotAgentPipelineService,
        id: Long,
        repoPath: String?,
        finnOutput: String? = null,
    ): AgentPipelineRun {
        val run = AgentPipelineRun(id = id, rawText = "t", repoPath = repoPath, featureSlug = "slug", createdAt = 0L)
        finnOutput?.let { run.finn.output = it }
        svc.registry.put(id, run)
        return run
    }

    @Test
    fun `PipelineWriteErrorCode enum values`() {
        assertEquals(4, PipelineWriteErrorCode.values().size)
        assertEquals(PipelineWriteErrorCode.PATH_TRAVERSAL, PipelineWriteErrorCode.valueOf("PATH_TRAVERSAL"))
        assertEquals(PipelineWriteErrorCode.DIR_CREATE_FAILED, PipelineWriteErrorCode.valueOf("DIR_CREATE_FAILED"))
        assertEquals(PipelineWriteErrorCode.WRITE_FAILED, PipelineWriteErrorCode.valueOf("WRITE_FAILED"))
        assertEquals(PipelineWriteErrorCode.SECURITY_DENIED, PipelineWriteErrorCode.valueOf("SECURITY_DENIED"))
    }

    @Test
    fun `parseAllStageOutputs combines all stage outputs`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 1L, rawText = "", repoPath = null, featureSlug = "x", createdAt = 0L)
        run.finn.output = fileBlock("a.py", "hello")
        run.faye.output = fileBlock("b.py", "bye")

        val parsed = svc.parseAllStageOutputs(run)

        assertEquals(2, parsed.size)
        assertTrue(parsed.any { it.path == "a.py" })
        assertTrue(parsed.any { it.path == "b.py" })
    }

    @Test
    fun `parseAllStageOutputs returns empty when all stages are blank`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 1L, rawText = "", repoPath = null, featureSlug = "x", createdAt = 0L)

        assertTrue(svc.parseAllStageOutputs(run).isEmpty())
    }

    @Test
    fun `safeMessage returns empty string for exception with null message`() {
        assertEquals("", makeService().safeMessage(RuntimeException()))
    }

    @Test
    fun `safeMessage returns the exception message`() {
        assertEquals("boom", makeService().safeMessage(RuntimeException("boom")))
    }

    @Test
    fun `providerWritesFilesDuringPipeline returns true when provider is null`() {
        val svc = makeService()
        whenever(aiConfigPort.findAiConfig()).thenReturn(null)
        assertTrue(svc.providerWritesFilesDuringPipeline())
    }

    @Test
    fun `providerWritesFilesDuringPipeline returns true when provider is blank`() {
        val svc = makeService()
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(aiProvider = "   "))
        assertTrue(svc.providerWritesFilesDuringPipeline())
    }

    @Test
    fun `providerWritesFilesDuringPipeline returns true when provider is copilot id`() {
        val svc = makeService()
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(aiProvider = "copilot"))
        assertTrue(svc.providerWritesFilesDuringPipeline())
    }

    @Test
    fun `providerWritesFilesDuringPipeline returns true when provider is copilot-cli string`() {
        val svc = makeService()
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(aiProvider = "copilot-cli"))
        assertTrue(svc.providerWritesFilesDuringPipeline())
    }

    @Test
    fun `providerWritesFilesDuringPipeline returns false when provider is ollama`() {
        val svc = makeService()
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(aiProvider = "ollama"))
        assertTrue(!svc.providerWritesFilesDuringPipeline())
    }

    @Test
    fun `providerWritesFilesDuringPipeline returns false when provider is openai`() {
        val svc = makeService()
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(aiProvider = "openai"))
        assertTrue(!svc.providerWritesFilesDuringPipeline())
    }

    @Test
    fun `providerWritesFilesDuringPipeline returns false when provider is lmstudio`() {
        val svc = makeService()
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(aiProvider = "lmstudio"))
        assertTrue(!svc.providerWritesFilesDuringPipeline())
    }

    @Test
    fun `previewWriteFiles returns null for unknown id`() {
        assertNull(makeService().previewWriteFiles(99L))
    }

    @Test
    fun `previewWriteFiles combines every stage output with its size`() {
        val svc = makeService()
        val run = runWith(svc, 1L, "/repo", fileBlock("a.kt", "aaaa"))
        run.faye.output = fileBlock("b.js", "bb")
        run.tess.output = fileBlock("c.kt", "ccc")
        run.syd.output = fileBlock("d.kt", "dddd")
        run.quinn.output = fileBlock("e.kt", "eeeee")
        run.sage.output = fileBlock("f.kt", "ffffff")
        run.vera.output = fileBlock("g.yml", "ggggggg")

        val preview = svc.previewWriteFiles(1L)!!

        assertEquals("/repo", preview.repoPath)
        assertNull(preview.errorKey)
        assertEquals(
            listOf("a.kt", "b.js", "c.kt", "d.kt", "e.kt", "f.kt", "g.yml"),
            preview.files.map { it.path },
        )
        assertEquals(listOf(4, 2, 3, 4, 5, 6, 7), preview.files.map { it.size })
    }

    @Test
    fun `previewWriteFiles returns the finn error key when finn output is blank`() {
        val svc = makeService()
        runWith(svc, 2L, "/repo")

        val preview = svc.previewWriteFiles(2L)!!

        assertEquals("pipeline.be_finn_no_output", preview.errorKey)
        assertTrue(preview.files.isEmpty())
    }

    @Test
    fun `writeFiles returns null for unknown id`() {
        assertNull(makeService().writeFiles(99L, true))
    }

    @Test
    fun `writeFiles returns no-repo error when repo path is missing`() {
        val svc = makeService()
        runWith(svc, 3L, null, fileBlock("a.kt", "a"))

        assertEquals("pipeline.be_no_repo_linked", svc.writeFiles(3L, true)!!.errorKey)
    }

    @Test
    fun `writeFiles returns finn error when finn output is blank`() {
        val svc = makeService()
        runWith(svc, 4L, "/tmp")

        assertEquals("pipeline.be_finn_no_output", svc.writeFiles(4L, true)!!.errorKey)
    }

    @Test
    fun `writeFiles returns invalid repo path error when canonicalisation fails`() {
        val svc = makeService()
        runWith(svc, 5L, "/path/\u0000/invalid", fileBlock("a.kt", "a"))

        val outcome = svc.writeFiles(5L, true)!!

        assertEquals("config.be_invalid_repo_path_msg", outcome.errorKey)
        assertTrue(!outcome.errorArg.isNullOrEmpty())
    }

    @Test
    fun `writeFiles falls back to an empty error arg when the exception has no message`() {
        val real = JavaFileSystemAdapter()
        val failing =
            object : FileSystemPort by real {
                override fun canonicalFile(file: File): File = throw RuntimeException()
            }
        val svc = makeService(overrideFilePort = failing)
        runWith(svc, 6L, "/repo", fileBlock("a.kt", "a"))

        val outcome = svc.writeFiles(6L, true)!!

        assertEquals("config.be_invalid_repo_path_msg", outcome.errorKey)
        assertEquals("", outcome.errorArg)
    }

    @Test
    fun `writeFiles returns repo-dir error when the directory does not exist`() {
        val svc = makeService()
        runWith(svc, 7L, "/nonexistent/repo-${System.nanoTime()}", fileBlock("a.kt", "a"))

        val outcome = svc.writeFiles(7L, true)!!

        assertEquals("config.be_repo_dir_not_exists", outcome.errorKey)
        assertTrue(!outcome.errorArg.isNullOrEmpty())
    }

    @Test
    fun `writeFiles returns no-write-permissions error when the directory is not writable`(
        @TempDir tempDir: File,
    ) {
        tempDir.setWritable(false)
        try {
            val svc = makeService()
            runWith(svc, 8L, tempDir.absolutePath, fileBlock("a.kt", "a"))

            val outcome = svc.writeFiles(8L, true)!!

            assertEquals("config.be_no_write_permissions", outcome.errorKey)
            assertTrue(outcome.showWritePermissionHint)
        } finally {
            tempDir.setWritable(true)
        }
    }

    @Test
    fun `writeFiles skips writing when rex is done and native writer is active`(
        @TempDir tempDir: File,
    ) {
        val svc = makeService()
        val run = runWith(svc, 9L, tempDir.absolutePath, fileBlock("src/App.kt", "class App"))
        run.rex.status = StageStatus.DONE

        val outcome = svc.writeFiles(9L, true)!!

        assertEquals("config.be_rex_already_written", outcome.infoKey)
        assertTrue(outcome.written.isEmpty())
        assertTrue(!File(tempDir, "src/App.kt").exists())
    }

    @Test
    fun `writeFiles writes when rex is done but provider is not a native writer`(
        @TempDir tempDir: File,
    ) {
        val svc = makeService()
        val run = runWith(svc, 10L, tempDir.absolutePath, fileBlock("src/App.kt", "class App"))
        run.rex.status = StageStatus.DONE
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(aiProvider = "ollama"))

        val outcome = svc.writeFiles(10L, null)!!

        assertEquals(listOf("src/App.kt"), outcome.written)
        assertTrue(File(tempDir, "src/App.kt").exists())
    }

    @Test
    fun `writeFiles detects the provider when native writer is null`(
        @TempDir tempDir: File,
    ) {
        val svc = makeService()
        val run = runWith(svc, 11L, tempDir.absolutePath, fileBlock("src/App.kt", "class App"))
        run.rex.status = StageStatus.DONE
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(aiProvider = "copilot"))

        assertEquals("config.be_rex_already_written", svc.writeFiles(11L, null)!!.infoKey)
    }

    @Test
    fun `writeFiles returns auto written files and deleted entries`(
        @TempDir tempDir: File,
    ) {
        val svc = makeService()
        val run = runWith(svc, 12L, tempDir.absolutePath, fileBlock("src/App.kt", "class App"))
        run.filesAutoWritten = true
        run.autoWrittenFiles = listOf("src/App.kt")
        run.autoDeletedFiles = listOf("old.kt")
        run.autoWriteErrors = listOf("raw")

        val outcome = svc.writeFiles(12L, true)!!

        assertEquals(listOf("src/App.kt"), outcome.written)
        assertEquals(listOf("old.kt"), outcome.deleted)
        assertEquals(listOf("raw"), outcome.errors.map { it.message })
        assertEquals("config.be_rex_already_written", outcome.infoKey)
    }

    @Test
    fun `writeFiles creates nested directories and writes content`(
        @TempDir tempDir: File,
    ) {
        val svc = makeService()
        runWith(svc, 13L, tempDir.absolutePath, fileBlock("src/main/App.kt", "class App"))

        val outcome = svc.writeFiles(13L, true)!!

        assertEquals(listOf("src/main/App.kt"), outcome.written)
        assertTrue(outcome.errors.isEmpty())
        assertNull(outcome.deleted)
        assertEquals("class App", File(tempDir, "src/main/App.kt").readText())
    }

    @Test
    fun `writeFiles writes into an existing directory`(
        @TempDir tempDir: File,
    ) {
        File(tempDir, "src").mkdirs()
        val svc = makeService()
        runWith(svc, 14L, tempDir.absolutePath, fileBlock("src/App.kt", "class App"))

        assertEquals(listOf("src/App.kt"), svc.writeFiles(14L, true)!!.written)
    }

    @Test
    fun `writeFiles rejects a canonical path outside the root through a symlink`(
        @TempDir tempDir: File,
    ) {
        val outside = File(tempDir.parentFile, "outside-${System.nanoTime()}").apply { mkdirs() }
        val link = File(tempDir, "escape")
        try {
            java.nio.file.Files
                .createSymbolicLink(link.toPath(), outside.toPath())
        } catch (_: Exception) {
            outside.delete()
            return
        }
        try {
            val svc = makeService()
            runWith(svc, 15L, tempDir.absolutePath, fileBlock("escape/evil.kt", "x"))

            val outcome = svc.writeFiles(15L, true)!!

            assertTrue(outcome.written.isEmpty())
            assertEquals(PipelineWriteErrorCode.PATH_TRAVERSAL, outcome.errors.single().code)
        } finally {
            outside.delete()
        }
    }

    @Test
    fun `writeFiles reports directory creation failures`(
        @TempDir tempDir: File,
    ) {
        File(tempDir, "src").writeText("blocker")
        val svc = makeService()
        runWith(svc, 16L, tempDir.absolutePath, fileBlock("src/deep/File.kt", "class F"))

        val outcome = svc.writeFiles(16L, true)!!

        assertTrue(outcome.written.isEmpty())
        val error = outcome.errors.single()
        assertEquals(PipelineWriteErrorCode.DIR_CREATE_FAILED, error.code)
        assertEquals(File(tempDir, "src/deep").canonicalPath, error.detail)
    }

    @Test
    fun `writeFiles reports write failures when the parent is a file`(
        @TempDir tempDir: File,
    ) {
        File(tempDir, "src").writeText("blocker")
        val svc = makeService()
        runWith(svc, 17L, tempDir.absolutePath, fileBlock("src/App.kt", "class App"))

        val error = svc.writeFiles(17L, true)!!.errors.single()

        assertEquals(PipelineWriteErrorCode.WRITE_FAILED, error.code)
        assertTrue(error.detail != null)
    }

    @Test
    fun `writeFiles reports security exceptions`(
        @TempDir tempDir: File,
    ) {
        val real = JavaFileSystemAdapter()
        val failing =
            object : FileSystemPort by real {
                override fun writeFile(
                    file: File,
                    content: String,
                ): Unit = throw SecurityException("denied")
            }
        val svc = makeService(overrideFilePort = failing)
        runWith(svc, 18L, tempDir.absolutePath, fileBlock("src/App.kt", "class App"))

        val error = svc.writeFiles(18L, true)!!.errors.single()

        assertEquals(PipelineWriteErrorCode.SECURITY_DENIED, error.code)
        assertEquals("denied", error.detail)
    }

    @Test
    fun `writeFiles reports non-security failures from target canonicalisation`(
        @TempDir tempDir: File,
    ) {
        val real = JavaFileSystemAdapter()
        var calls = 0
        val failing =
            object : FileSystemPort by real {
                override fun canonicalFile(file: File): File {
                    calls++
                    if (calls > 1) throw RuntimeException("canonical boom")
                    return real.canonicalFile(file)
                }
            }
        val svc = makeService(overrideFilePort = failing)
        runWith(svc, 19L, tempDir.absolutePath, fileBlock("src/App.kt", "class App"))

        val error = svc.writeFiles(19L, true)!!.errors.single()

        assertEquals(PipelineWriteErrorCode.WRITE_FAILED, error.code)
        assertEquals("canonical boom", error.detail)
    }

    @Test
    fun `writeFiles returns deleted entries when the agent deletes files`(
        @TempDir tempDir: File,
    ) {
        val old = File(tempDir, "old.kt")
        old.writeText("old")
        val svc = makeService()
        val run =
            runWith(
                svc,
                21L,
                tempDir.absolutePath,
                fileBlock("src/App.kt", "class App") + "\nDELETED old.kt\n",
            )
        val outcome = svc.writeFiles(21L, true)!!
        assertEquals(listOf("old.kt"), outcome.deleted)
    }

    @Test
    fun `writeFiles handles multiple output stages`(
        @TempDir tempDir: File,
    ) {
        val svc = makeService()
        val run = runWith(svc, 20L, tempDir.absolutePath, fileBlock("src/App.php", "class App {}"))
        run.tess.output = fileBlock("tests/AppTest.php", "class AppTest {}")
        run.sage.output = fileBlock("src/Service.php", "class Service {}")

        val outcome = svc.writeFiles(20L, true)!!

        assertEquals(3, outcome.written.size)
        assertTrue(File(tempDir, "tests/AppTest.php").exists())
        assertTrue(File(tempDir, "src/Service.php").readText().contains("Service"))
    }
}
