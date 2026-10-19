package com.stacklens.adapter.out.codeqa

import com.stacklens.adapter.out.filesystem.LocalDirectoryScanAdapter
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class ComposeFileSelectorMissedBranchTest {
    private val copilotPort: CopilotStreamingPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val directoryScan = LocalDirectoryScanAdapter()
    private val discovery = ComposeFileDiscovery(directoryScan)
    private val treeLister = RepoFileTreeLister(directoryScan)
    private val selector = ComposeFileSelector(discovery, ComposeFileAiSelector(copilotPort, promptLoader))

    private fun mrData() =
        MrFetchPort.MrData(
            mrUrl = "https://gitlab.example.com/group/project/-/merge_requests/7",
            platform = "gitlab",
            title = "Add compose profile",
            description = "desc",
            sourceBranch = "feature",
            targetBranch = "main",
            authorName = "author",
            state = "opened",
            diffText = "diff",
            changedFiles = listOf("docker-compose.yml"),
        )

    private fun stubAi(answer: String) {
        whenever(promptLoader.get(any(), any(), anyOrNull())).thenReturn("prompt")
        whenever(copilotPort.resolveModel()).thenReturn("model")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(answer)
    }

    private fun twoComposeFiles(root: Path) {
        root.resolve("docker-compose.yml").toFile().writeText("version: '3'")
        root.resolve("sub").toFile().mkdirs()
        root.resolve("sub/docker-compose.yml").toFile().writeText("version: '3'")
    }

    @Test
    fun `ai answer without a number falls back to the first candidate`(
        @TempDir root: Path,
    ) {
        twoComposeFiles(root)
        stubAi("the second file looks better")

        val chosen = selector.findAndSelect(root.toFile(), mrData()) { }

        assertEquals("docker-compose.yml", chosen!!.name)
    }

    @Test
    fun `ai answer out of range falls back to the first candidate`(
        @TempDir root: Path,
    ) {
        twoComposeFiles(root)
        stubAi("99")

        val chosen = selector.findAndSelect(root.toFile(), mrData()) { }

        assertEquals("docker-compose.yml", chosen!!.name)
    }

    @Test
    fun `ai answer with a number beyond the integer range falls back to the first candidate`(
        @TempDir root: Path,
    ) {
        twoComposeFiles(root)
        stubAi("file 999999999999")

        val chosen = selector.findAndSelect(root.toFile(), mrData()) { }

        assertEquals("docker-compose.yml", chosen!!.name)
    }

    @Test
    fun `ai failure falls back to the first candidate`(
        @TempDir root: Path,
    ) {
        twoComposeFiles(root)
        whenever(promptLoader.get(any(), any(), anyOrNull())).thenReturn("prompt")
        whenever(copilotPort.resolveModel()).thenReturn("model")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("copilot down"))

        val chosen = selector.findAndSelect(root.toFile(), mrData()) { }

        assertEquals("docker-compose.yml", chosen!!.name)
    }

    @Test
    fun `listing descends into directories it cannot list`(
        @TempDir root: Path,
    ) {
        val unreadable =
            object : File(root.resolve("unreadable").toString()) {
                override fun isDirectory(): Boolean = true

                override fun listFiles(): Array<File>? = null
            }
        val listed =
            object : File(root.resolve("listed").toString()) {
                override fun isDirectory(): Boolean = true

                override fun listFiles(): Array<File> = arrayOf(File(root.resolve("inner.txt").toString()))
            }
        val rootDir =
            object : File(root.toString()) {
                override fun listFiles(): Array<File> =
                    arrayOf(
                        File(root.resolve("plain.txt").toString()),
                        listed,
                        unreadable,
                    )
            }

        val listing = treeLister.buildFileList(rootDir, maxFiles = 80)

        assertTrue(listing.contains("plain.txt"))
        assertTrue(listing.contains("inner.txt"))
        assertTrue(listing.contains("unreadable"))
    }

    @Test
    fun `listing stops once the file budget is used up`(
        @TempDir root: Path,
    ) {
        root.resolve(".hidden").toFile().mkdirs()
        root.resolve("node_modules").toFile().mkdirs()
        root.resolve("vendor").toFile().mkdirs()
        root.resolve("a.txt").toFile().writeText("x")
        root.resolve("b.txt").toFile().writeText("x")

        val listing = treeLister.buildFileList(root.toFile(), maxFiles = 1)

        assertTrue(listing.contains("a.txt"))
        assertEquals(1, listing.lines().count { it.isNotBlank() })
    }

    @Test
    fun `listing of a missing directory yields an empty string`(
        @TempDir root: Path,
    ) {
        assertEquals("", treeLister.buildFileList(File(root.resolve("missing").toString()), maxFiles = 5))
    }
}
