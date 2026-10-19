package com.stacklens.adapter.out.codeqa

import com.stacklens.adapter.out.filesystem.LocalDirectoryScanAdapter
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class ComposeFileSelectorTest {
    private val copilotPort: CopilotStreamingPort = mock()
    private val promptLoader: PromptReaderPort = mock()

    private fun directoryScan() = LocalDirectoryScanAdapter()

    private fun discovery() = ComposeFileDiscovery(directoryScan())

    private fun treeLister() = RepoFileTreeLister(directoryScan())

    private fun selector() = ComposeFileSelector(discovery(), ComposeFileAiSelector(copilotPort, promptLoader))

    @Test
    fun `findAllComposeFiles returns empty list when no compose file is present`(
        @TempDir tmp: Path,
    ) {
        val results = discovery().findComposeFiles(tmp.toFile())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findAllComposeFiles returns a single docker-compose yml at the root`(
        @TempDir tmp: Path,
    ) {
        java.io.File(tmp.toFile(), "docker-compose.yml").writeText("services: {}")
        val results = discovery().findComposeFiles(tmp.toFile())
        assertEquals(1, results.size)
        assertEquals("docker-compose.yml", results[0].name)
    }

    @Test
    fun `findAllComposeFiles returns yaml variant when only that exists`(
        @TempDir tmp: Path,
    ) {
        java.io.File(tmp.toFile(), "docker-compose.yaml").writeText("services: {}")
        val results = discovery().findComposeFiles(tmp.toFile())
        assertEquals(1, results.size)
        assertEquals("docker-compose.yaml", results[0].name)
    }

    @Test
    fun `findAllComposeFiles finds docker-compose inside a subdirectory`(
        @TempDir tmp: Path,
    ) {
        val subdir = tmp.resolve("infra").toFile().apply { mkdirs() }
        java.io.File(subdir, "docker-compose.yml").writeText("services: {}")
        val results = discovery().findComposeFiles(tmp.toFile())
        assertEquals(1, results.size)
        assertTrue(results[0].path.contains("infra/docker-compose.yml"))
    }

    @Test
    fun `findAllComposeFiles skips node_modules directory`(
        @TempDir tmp: Path,
    ) {
        val nm = tmp.resolve("node_modules").toFile().apply { mkdirs() }
        java.io.File(nm, "docker-compose.yml").writeText("nested: true")
        java.io.File(tmp.toFile(), "docker-compose.yml").writeText("root: true")
        val results = discovery().findComposeFiles(tmp.toFile())
        assertEquals(1, results.size)
        assertTrue(results[0].name == "docker-compose.yml" && results[0].parentFile == tmp.toFile())
    }

    @Test
    fun `findAllComposeFiles skips vendor, build, target, gradle directories`(
        @TempDir tmp: Path,
    ) {
        listOf("vendor", "build", "target", "__pycache__").forEach { dir ->
            val subdir = tmp.resolve(dir).toFile().apply { mkdirs() }
            java.io.File(subdir, "docker-compose.yml").writeText("nested: $dir")
        }
        val results = discovery().findComposeFiles(tmp.toFile())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findAllComposeFiles skips hidden subdirectories that are not the root`(
        @TempDir tmp: Path,
    ) {
        val subdir = java.io.File(tmp.toFile(), "foo").apply { mkdirs() }
        val gitInside = java.io.File(subdir, ".git").apply { mkdirs() }
        java.io.File(gitInside, "docker-compose.yml").writeText("git: true")
        java.io.File(subdir, "docker-compose.yml").writeText("foo: true")
        val results = discovery().findComposeFiles(tmp.toFile())
        assertEquals(1, results.size)
        assertTrue(results[0].path.contains("foo/docker-compose.yml"))
    }

    @Test
    fun `findAllComposeFiles returns results sorted by depth`(
        @TempDir tmp: Path,
    ) {
        java.io.File(tmp.toFile(), "docker-compose.yml").writeText("root")
        val sub = java.io.File(tmp.toFile(), "sub").apply { mkdirs() }
        val subsub = java.io.File(sub, "subsub").apply { mkdirs() }
        java.io.File(subsub, "docker-compose.yml").writeText("deep")
        val results = discovery().findComposeFiles(tmp.toFile())
        assertEquals(2, results.size)
        assertTrue(results[0].path.contains("/docker-compose.yml"))
        assertTrue(!results[1].path.contains("subsub/docker-compose.yml").not())
    }

    @Test
    fun `findAndSelect returns the single compose file when only one exists`() {
        val tmp = java.io.File.createTempFile("cs-", "")
        tmp.delete()
        tmp.mkdirs()
        try {
            val cfg = java.io.File(tmp, "docker-compose.yml")
            cfg.writeText("services: {}")
            val mrData =
                MrFetchPort.MrData(
                    mrUrl = "u",
                    platform = "g",
                    title = "t",
                    description = "",
                    sourceBranch = "b",
                    targetBranch = "main",
                    authorName = "a",
                    state = "open",
                    diffText = "",
                    changedFiles = listOf(),
                    diffBaseSha = "",
                    diffStartSha = "",
                    diffHeadSha = "",
                )
            val sel = selector()
            val result = sel.findAndSelect(tmp, mrData) { }
            assertEquals(cfg, result)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `findAndSelect returns null when no compose file exists`() {
        val tmp = java.io.File.createTempFile("cs-empty-", "")
        tmp.delete()
        tmp.mkdirs()
        try {
            val mrData =
                MrFetchPort.MrData(
                    mrUrl = "u",
                    platform = "g",
                    title = "t",
                    description = "",
                    sourceBranch = "b",
                    targetBranch = "main",
                    authorName = "a",
                    state = "open",
                    diffText = "",
                    changedFiles = listOf(),
                    diffBaseSha = "",
                    diffStartSha = "",
                    diffHeadSha = "",
                )
            val result = selector().findAndSelect(tmp, mrData) { }
            assertNull(result)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `buildFileList returns empty string for an empty directory`() {
        val tmp = java.io.File.createTempFile("cs-", "")
        tmp.delete()
        tmp.mkdirs()
        try {
            assertEquals("", treeLister().buildFileList(tmp))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `buildFileList lists top-level files`() {
        val tmp = java.io.File.createTempFile("cs-", "")
        tmp.delete()
        tmp.mkdirs()
        try {
            java.io.File(tmp, "a.txt").writeText("a")
            java.io.File(tmp, "b.txt").writeText("b")
            val sb = treeLister().buildFileList(tmp)
            assertTrue(sb.contains("a.txt"))
            assertTrue(sb.contains("b.txt"))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `buildFileList skips hidden files and ignored directories`() {
        val tmp = java.io.File.createTempFile("cs-", "")
        tmp.delete()
        tmp.mkdirs()
        try {
            java.io.File(tmp, "visible.txt").writeText("v")
            java.io.File(tmp, ".hidden").writeText("h")
            val nm = java.io.File(tmp, "node_modules").apply { mkdirs() }
            java.io.File(nm, "x.txt").writeText("x")
            val sb = treeLister().buildFileList(tmp)
            assertTrue(sb.contains("visible.txt"))
            assertTrue(!sb.contains(".hidden"))
            assertTrue(!sb.contains("node_modules"))
            assertTrue(!sb.contains("x.txt"))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `buildFileList stops at maxFiles limit`() {
        val tmp = java.io.File.createTempFile("cs-", "")
        tmp.delete()
        tmp.mkdirs()
        try {
            repeat(10) { i -> java.io.File(tmp, "f$i.txt").writeText("x") }
            val sb = treeLister().buildFileList(tmp, maxFiles = 5)
            assertTrue(sb.lines().count { it.isNotBlank() } <= 5)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `buildFileList recurses into directories`() {
        val tmp = java.io.File.createTempFile("cs-", "")
        tmp.delete()
        tmp.mkdirs()
        try {
            val sub = java.io.File(tmp, "sub").apply { mkdirs() }
            java.io.File(sub, "nested.txt").writeText("n")
            val sb = treeLister().buildFileList(tmp)
            assertTrue(sb.contains("sub"))
            assertTrue(sb.contains("nested.txt"))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `selectBestComposeFile falls back to first on AI exception`() {
        val tmp = java.io.File.createTempFile("cs-", "")
        tmp.delete()
        tmp.mkdirs()
        try {
            val f1 = java.io.File(tmp, "docker-compose.yml").apply { writeText("a") }
            val f2 = java.io.File(tmp, "sub").apply { mkdirs() }
            val f2c = java.io.File(f2, "docker-compose.yml").apply { writeText("b") }
            val sel = selector()
            whenever(copilotPort.resolveModel()).thenReturn("test-model")
            whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenThrow(RuntimeException("boom"))
            val mrData =
                MrFetchPort.MrData(
                    mrUrl = "u",
                    platform = "g",
                    title = "t",
                    description = "",
                    sourceBranch = "b",
                    targetBranch = "main",
                    authorName = "a",
                    state = "open",
                    diffText = "",
                    changedFiles = listOf(),
                    diffBaseSha = "",
                    diffStartSha = "",
                    diffHeadSha = "",
                )
            whenever(promptLoader.get(any(), any(), any())).thenReturn("ignored")
            val result = sel.findAndSelect(tmp, mrData) { }
            assertNotNull(result)
        } finally {
            tmp.deleteRecursively()
        }
    }
}
