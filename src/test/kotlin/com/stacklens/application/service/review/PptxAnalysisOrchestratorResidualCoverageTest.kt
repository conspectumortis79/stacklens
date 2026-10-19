package com.stacklens.application.service.review

import com.stacklens.adapter.out.review.pptx.PptxSlideHtmlRenderer
import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
import com.stacklens.application.port.out.prompt.PromptContext
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.review.PptxStructurePort
import com.stacklens.application.service.ai.AiCaller
import com.stacklens.domain.model.PptxDeckDto
import com.stacklens.domain.model.PptxSlideDto
import com.stacklens.domain.model.PptxThemeDto
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PptxAnalysisOrchestratorResidualCoverageTest {
    private val fakePromptReader: PromptReaderPort = mock()
    private val questionExtractor = PptxQuestionExtractor(TestJsonCodec.create())
    private val answerMapper = PptxAnswerMapper(TestJsonCodec.create())
    private val renderer = PptxSlideHtmlRenderer()
    private val storage =
        com.stacklens.adapter.out.review.pptx
            .PptxStorageAdapter()

    private fun setupPromptReader() {
        whenever(fakePromptReader.context(any())).thenAnswer {
            PromptContext(
                mapOf(
                    "review.pptxStage1Prompt" to "STAGE1 {sprintData} {deckStructure}",
                    "review.pptxStage2Prompt" to "STAGE2 {sprintData} {questionPlan} {pptxLangDirective}",
                    "review.pptxLangDirective" to "target={targetLang}",
                ),
            )
        }
        whenever(fakePromptReader.get(any(), any(), any())).thenAnswer { "[${it.arguments[1]}]" }
    }

    private fun twoSlideDeck(): PptxDeckDto =
        PptxDeckDto(
            slideWidthPx = 1280,
            slideHeightPx = 720,
            theme = PptxThemeDto(emptyMap(), "Calibri", "Calibri", null),
            slides =
                listOf(
                    PptxSlideDto(index = 1, title = "One", backgroundColor = null, shapes = emptyList(), notes = null, rawText = ""),
                    PptxSlideDto(index = 2, title = "Two", backgroundColor = null, shapes = emptyList(), notes = null, rawText = ""),
                ),
            sourceFileName = "two-slides.pptx",
        )

    @Test
    fun `slides without a question plan entry get empty question ids`() {
        setupPromptReader()
        val fakeStructure =
            object : PptxStructurePort {
                override fun supports(
                    filename: String,
                    content: ByteArray,
                ): Boolean = true

                override fun extract(
                    filename: String,
                    content: ByteArray,
                ): PptxDeckDto = twoSlideDeck()
            }
        val orch =
            PptxAnalysisOrchestrator(
                structureExtractor = fakeStructure,
                jsonCodec = TestJsonCodec.create(),
                questionExtractor = questionExtractor,
                answerMapper = answerMapper,
                renderer = renderer,
                storage = storage,
                promptAssembler = PptxPromptAssembler(fakePromptReader),
                analysisLog = PptxAnalysisLog(TestJsonCodec.create()),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )

        val aiCaller =
            object : AiCaller {
                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutSec: Long,
                ): String = throw IllegalStateException("ai down")
            }

        val analysis =
            ReviewAnalysis(
                id = 7771L,
                jiraSprintUrl = "https://jira",
                jiraConfigId = 1L,
                status = "RUNNING",
            )
        storage.saveUpload(analysis.id, "two-slides.pptx", byteArrayOf(0x50, 0x4B, 0x03, 0x04))

        val path =
            orch.runPptxAnalysis(
                analysis,
                SprintInfo(1L, "Sprint 1", "active", "2024-01-01", "2024-01-14", "goal"),
                emptyList<SprintIssue>(),
                "de",
                "gpt-4",
                aiCaller,
            )

        assertNotNull(path)
        assertNotNull(analysis.processLogJson)
        assertTrue(analysis.processLogJson!!.contains("Stage 1 fehlgeschlagen"))
        storage.cleanup(analysis.id)
    }
}
