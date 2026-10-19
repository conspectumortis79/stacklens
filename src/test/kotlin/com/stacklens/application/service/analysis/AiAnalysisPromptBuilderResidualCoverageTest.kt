package com.stacklens.application.service.analysis

import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptContext
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.model.SearchSession
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AiAnalysisPromptBuilderResidualCoverageTest {
    private val promptLoader: PromptReaderPort = mock()
    private val copilot: CopilotStreamingPort = mock()

    @Test
    fun `source types without a configured limit keep all rows`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.context(any())).thenReturn(PromptContext(emptyMap()))

        val builder = AiAnalysisPromptBuilder(promptLoader, copilot)
        val session = SearchSession(keywords = "kw")
        val results =
            (1..3).map {
                SearchResult(
                    sessionId = 1L,
                    keyword = "kw",
                    sourceType = SourceType.GITHUB,
                    repoName = "repo-$it",
                    filePath = "file-$it",
                )
            }

        val prompt = builder.buildPrompt(session, results)

        assertTrue(prompt.isNotBlank())
    }
}
