package com.stacklens.application.service.review

import com.stacklens.adapter.out.review.pptx.PptxSlideHtmlRenderer
import com.stacklens.adapter.out.review.pptx.PptxStorageAdapter
import com.stacklens.application.port.out.PptxStoragePort
import com.stacklens.application.port.out.prompt.PromptContext
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.review.PptxStructurePort
import com.stacklens.application.service.ai.AiCaller
import com.stacklens.domain.model.PptxDeckDto
import com.stacklens.domain.model.PptxShapeDto
import com.stacklens.domain.model.PptxSlideDto
import com.stacklens.domain.model.PptxThemeDto
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.domain.model.TextRunDto
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PptxAnalysisOrchestratorFailurePathTest {
    private val promptReader: PromptReaderPort = mock()

    private val storage: PptxStoragePort = PptxStorageAdapter()

    private val codec = TestJsonCodec.create()

    private fun prompts() {
        whenever(promptReader.context(any())).thenAnswer {
            PromptContext(
                mapOf(
                    "review.pptxStage1Prompt" to "STAGE1 {sprintData} {deckStructure}",
                    "review.pptxStage2Prompt" to "STAGE2 {sprintData} {questionPlan} {pptxLangDirective}",
                    "review.pptxLangDirective" to "target={targetLang}",
                ),
            )
        }
        whenever(promptReader.get(any(), any(), any())).thenAnswer { invocation -> "[${invocation.arguments[1]}]" }
    }

    private fun deck(slides: List<Int>): PptxDeckDto =
        PptxDeckDto(
            slideWidthPx = 1280,
            slideHeightPx = 720,
            theme = PptxThemeDto(emptyMap(), "Calibri", "Calibri", null),
            slides =
                slides.map { index ->
                    PptxSlideDto(
                        index = index,
                        title = "Folie $index",
                        backgroundColor = null,
                        shapes =
                            listOf(
                                PptxShapeDto.TextShape(
                                    xPx = 40,
                                    yPx = 40,
                                    widthPx = 500,
                                    heightPx = 60,
                                    textRuns =
                                        listOf(
                                            TextRunDto(
                                                text = "Frage zu Folie $index",
                                                fontFamily = "Calibri",
                                                fontSizePt = 20,
                                                bold = false,
                                                italic = false,
                                                colorHex = "#000000",
                                                bullet = false,
                                            ),
                                        ),
                                    placeholderType = "TITLE",
                                ),
                            ),
                        notes = null,
                        rawText = "roh $index",
                    )
                },
            sourceFileName = "deck.pptx",
        )

    private fun orchestrator(structure: PptxStructurePort): PptxAnalysisOrchestrator =
        PptxAnalysisOrchestrator(
            structureExtractor = structure,
            jsonCodec = codec,
            questionExtractor = PptxQuestionExtractor(codec),
            answerMapper = PptxAnswerMapper(codec),
            renderer = PptxSlideHtmlRenderer(),
            storage = storage,
            promptAssembler = PptxPromptAssembler(promptReader),
            analysisLog = PptxAnalysisLog(codec),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private fun aiCaller(stage1: String): AiCaller =
        object : AiCaller {
            override fun execute(
                model: String,
                prompt: String,
                timeoutSec: Long,
            ): String = if (prompt.startsWith("STAGE1")) stage1 else "- s1-shape0: Antwort A\n- s1-shape1: Antwort B"
        }

    private fun analysis(
        id: Long,
        fileName: String?,
        logJson: String?,
    ): ReviewAnalysis =
        ReviewAnalysis(
            id = id,
            jiraSprintUrl = "https://jira.example.com/boards/1",
            jiraConfigId = 3L,
            status = "RUNNING",
            pptxFileName = fileName,
            processLogJson = logJson,
        )

    private fun structureThatReturns(value: PptxDeckDto): PptxStructurePort =
        object : PptxStructurePort {
            override fun supports(
                filename: String,
                content: ByteArray,
            ): Boolean = true

            override fun extract(
                filename: String,
                content: ByteArray,
            ): PptxDeckDto = value
        }

    @Test
    fun `two questions on one slide and a slide without questions survive the log round trip`() {
        prompts()
        storage.saveUpload(501L, "deck.pptx", byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        val plan =
            """
            {"slides":[{"slideIndex":1,"questions":[
              {"questionId":"s1-shape0","text":"Erste Frage?"},
              {"questionId":"s1-shape1","text":"Zweite Frage?"}
            ]}]}
            """.trimIndent()
        val target = orchestrator(structureThatReturns(deck(listOf(1, 2))))
        val record = analysis(501L, "deck.pptx", "{kein json")

        val path = target.runPptxAnalysis(record, null, emptyList(), "de", "gpt-4", aiCaller(plan))

        assertTrue(path != null)
        assertTrue(record.processLogJson!!.contains("HTML gespeichert"), record.processLogJson)
    }

    @Test
    fun `an extraction failure is logged and aborts the run`() {
        prompts()
        storage.saveUpload(502L, "deck.pptx", byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        val broken =
            object : PptxStructurePort {
                override fun supports(
                    filename: String,
                    content: ByteArray,
                ): Boolean = true

                override fun extract(
                    filename: String,
                    content: ByteArray,
                ): PptxDeckDto = throw IllegalStateException("no pptx parts")
            }
        val target = orchestrator(broken)
        val record = analysis(502L, "  ", null)

        val path = target.runPptxAnalysis(record, null, emptyList(), "de", "gpt-4", aiCaller("{\"slides\":[]}"))

        assertNull(path)
        assertTrue(record.processLogJson!!.contains("PPTX-Extraktion fehlgeschlagen"), record.processLogJson)
    }

    @Test
    fun `a named upload is found and the html result is returned`() {
        prompts()
        storage.saveUpload(503L, "deck.pptx", byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        val plan =
            """
            {"slides":[{"slideIndex":1,"questions":[
              {"questionId":"s1-shape0","text":"Erste Frage?"},
              {"questionId":"s1-shape1","text":"Zweite Frage mit \"Anfuehrung\""}
            ]}]}
            """.trimIndent()
        val target = orchestrator(structureThatReturns(deck(listOf(1, 2))))
        val record = analysis(503L, "deck.pptx", null)

        val path = target.runPptxAnalysis(record, null, emptyList(), "de", "gpt-4", aiCaller(plan))

        assertTrue(path != null)
        assertEquals(2, record.pptxSlideCount!!)
        assertTrue(record.pptxQuestionsJson!!.contains("s1-shape1"), record.pptxQuestionsJson)
        assertTrue(record.pptxAnswersJson!!.contains("Antwort A"), record.pptxAnswersJson)
    }
}
