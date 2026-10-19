package com.stacklens.adapter.out.codeqa

import com.stacklens.adapter.out.filesystem.LocalDirectoryScanAdapter
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class ComposeFileSelectorCoverageTest {
    private val copilotPort = mock<CopilotStreamingPort>()
    private val promptLoader = mock<PromptReaderPort>()
    private val directoryScan = LocalDirectoryScanAdapter()
    private val discovery = ComposeFileDiscovery(directoryScan)
    private val treeLister = RepoFileTreeLister(directoryScan)
    private val selector = ComposeFileSelector(discovery, ComposeFileAiSelector(copilotPort, promptLoader))

    private val mrData =
        MrFetchPort.MrData(
            mrUrl = "https://gitlab/x/-/merge_requests/1",
            platform = "gitlab",
            title = "Add service",
            description = "desc",
            sourceBranch = "feat",
            targetBranch = "main",
            authorName = "ada",
            state = "opened",
            diffText = "diff",
            changedFiles = listOf("docker-compose.yml"),
        )

    private fun aiAnswers(answer: String) {
        whenever(promptLoader.get(any(), any(), any())).thenReturn("PROMPT")
        whenever(copilotPort.resolveModel()).thenReturn("model")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(answer)
    }

    private fun twoCandidates(root: File): Pair<File, File> {
        val first = File(root, "docker-compose.yml").apply { writeText("a") }
        File(root, "deploy").mkdirs()
        val second = File(root, "deploy/docker-compose.yaml").apply { writeText("b") }
        return first to second
    }

    @Test
    fun `a single candidate is chosen without consulting the model`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        val only = File(root, "docker-compose.yml").apply { writeText("services: {}") }
        val emitted = StringBuilder()

        val chosen = selector.findAndSelect(root, mrData) { emitted.append(it) }

        assertEquals(only, chosen)
        assertTrue(emitted.toString().contains("docker-compose.yml"))
        verify(copilotPort, never()).execute(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `an empty repository has no compose file`(
        @TempDir dir: Path,
    ) {
        val emitted = StringBuilder()

        assertNull(selector.findAndSelect(dir.toFile(), mrData) { emitted.append(it) })
        assertEquals("", emitted.toString())
    }

    @Test
    fun `the model answer selects the matching candidate`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        val (_, second) = twoCandidates(root)
        aiAnswers("2")

        assertEquals(second, selector.findAndSelect(root, mrData) {})
    }

    @Test
    fun `a model answer without a number falls back to the first candidate`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        val (first, _) = twoCandidates(root)
        aiAnswers("cannot decide")

        assertEquals(first, selector.findAndSelect(root, mrData) {})
    }

    @Test
    fun `a model answer outside the candidate range falls back to the first candidate`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        val (first, _) = twoCandidates(root)
        aiAnswers("9")

        assertEquals(first, selector.findAndSelect(root, mrData) {})
    }

    @Test
    fun `a failing model call falls back to the first candidate`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        val (first, _) = twoCandidates(root)
        whenever(promptLoader.get(any(), any(), any())).thenReturn("PROMPT")
        whenever(copilotPort.resolveModel()).thenReturn("model")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenThrow(IllegalStateException("offline"))

        assertEquals(first, selector.findAndSelect(root, mrData) {})
    }

    @Test
    fun `hidden and dependency folders are never scanned`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        File(root, "docker-compose.yml").writeText("a")
        File(root, ".hidden").mkdirs()
        File(root, ".hidden/docker-compose.yml").writeText("hidden")
        File(root, "node_modules").mkdirs()
        File(root, "node_modules/docker-compose.yml").writeText("dep")

        val found = discovery.findComposeFiles(root)

        assertEquals(1, found.size)
        assertEquals("docker-compose.yml", found.single().name)
        assertEquals(root, found.single().parentFile)
    }

    @Test
    fun `a repository root with a dotted name is still scanned`(
        @TempDir dir: Path,
    ) {
        val outer = File(dir.toFile(), ".dot-root").apply { mkdir() }
        File(outer, "docker-compose.yaml").writeText("a")

        assertEquals(1, discovery.findComposeFiles(outer).size)
    }

    @Test
    fun `a file that is not a directory exposes no candidates`(
        @TempDir dir: Path,
    ) {
        val file = File(dir.toFile(), "regular.txt").apply { writeText("x") }

        assertEquals(emptyList<File>(), discovery.findComposeFiles(file))
        assertEquals("", treeLister.buildFileList(file))
    }

    @Test
    fun `the file listing stops at the configured budget`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        File(root, "a.txt").writeText("x")
        File(root, "b.txt").writeText("x")
        File(root, "nested").mkdirs()
        File(root, "nested/c.txt").writeText("x")

        val listing = treeLister.buildFileList(root, maxFiles = 2)

        assertEquals(2, listing.lines().count { it.isNotBlank() })
        assertTrue(listing.contains("a.txt"), listing)
    }

    @Test
    fun `dot files and dependency folders are left out of the listing`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        File(root, ".env").writeText("x")
        File(root, "vendor").mkdirs()
        File(root, "visible.md").writeText("x")

        val listing = treeLister.buildFileList(root)

        assertTrue(listing.contains("visible.md"), listing)
        assertTrue(!listing.contains(".env"), listing)
        assertTrue(!listing.contains("vendor"), listing)
    }

    @Test
    fun `the listing indents nested entries`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        File(root, "sub").mkdirs()
        File(root, "sub/inner.txt").writeText("x")

        val listing = treeLister.buildFileList(root)

        assertEquals(true, listing.contains("  inner.txt"), listing)
    }

    @Test
    fun `find and select reports the relative path of the winner`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        twoCandidates(root)
        aiAnswers("1")
        val emitted = StringBuilder()

        selector.findAndSelect(root, mrData) { emitted.append(it) }

        assertTrue(emitted.toString().contains("Compose:"), emitted.toString())
        verify(copilotPort).execute(eq("model"), eq("PROMPT"), eq(30L), anyOrNull())
    }
}
