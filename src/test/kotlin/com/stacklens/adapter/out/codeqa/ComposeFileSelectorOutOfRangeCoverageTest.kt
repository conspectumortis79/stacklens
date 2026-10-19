package com.stacklens.adapter.out.codeqa

import com.stacklens.adapter.out.filesystem.LocalDirectoryScanAdapter
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

class ComposeFileSelectorOutOfRangeCoverageTest {
    private val copilotPort = mock<CopilotStreamingPort>()
    private val promptLoader = mock<PromptReaderPort>()
    private val selector = ComposeFileSelector(ComposeFileDiscovery(LocalDirectoryScanAdapter()), ComposeFileAiSelector(copilotPort, promptLoader))

    @Test
    fun `an ai answer beyond the candidate count keeps the shallowest file`(
        @TempDir root: Path,
    ) {
        root.resolve("docker-compose.yml").toFile().writeText("version: '3'")
        root.resolve("sub").toFile().mkdirs()
        root.resolve("sub/docker-compose.yml").toFile().writeText("version: '3'")
        whenever(promptLoader.get(any(), any(), anyOrNull())).thenReturn("prompt")
        whenever(copilotPort.resolveModel()).thenReturn("model")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("9")

        val chosen = selector.findAndSelect(root.toFile(), mrData()) { }

        assertEquals(root.resolve("docker-compose.yml").toFile(), chosen)
    }

    @Test
    fun `an ai answer of zero keeps the shallowest file`(
        @TempDir root: Path,
    ) {
        root.resolve("docker-compose.yml").toFile().writeText("version: '3'")
        root.resolve("sub").toFile().mkdirs()
        root.resolve("sub/docker-compose.yml").toFile().writeText("version: '3'")
        whenever(promptLoader.get(any(), any(), anyOrNull())).thenReturn("prompt")
        whenever(copilotPort.resolveModel()).thenReturn("model")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("0. docker-compose.yml")

        val chosen = selector.findAndSelect(root.toFile(), mrData()) { }

        assertEquals(root.resolve("docker-compose.yml").toFile(), chosen)
    }

    private fun mrData(): MrFetchPort.MrData =
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
}
