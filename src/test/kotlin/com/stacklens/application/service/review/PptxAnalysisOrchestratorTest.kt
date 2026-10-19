package com.stacklens.application.service.review

import com.stacklens.adapter.out.review.pptx.PptxSlideHtmlRenderer
import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

class PptxAnalysisOrchestratorTest {
    private val fakePromptReader: PromptReaderPort = mock()
    private val questionExtractor = PptxQuestionExtractor(TestJsonCodec.create())
    private val answerMapper = PptxAnswerMapper(TestJsonCodec.create())
    private val renderer = PptxSlideHtmlRenderer()
    private val storage: com.stacklens.application.port.out.PptxStoragePort =
        com.stacklens.adapter.out.review.pptx
            .PptxStorageAdapter()

    private fun setupDefaultPromptReader() {
        whenever(fakePromptReader.context(any())).thenAnswer {
            PromptContext(
                mapOf(
                    "review.pptxStage1Prompt" to "STAGE1 {sprintData} {deckStructure}",
                    "review.pptxStage2Prompt" to "STAGE2 {sprintData} {questionPlan} {pptxLangDirective}",
                    "review.pptxLangDirective" to "Heuristik: target={targetLang}",
                ),
            )
        }
        whenever(fakePromptReader.get(any(), any(), any())).thenAnswer {
            "[${it.arguments[1]}]"
        }
    }

    private fun orchestrator(
        aiCaller: AiCaller,
        structureProvider: () -> PptxDeckDto = { sampleDeck() },
    ): PptxAnalysisOrchestrator {
        val fakeStructure =
            object : PptxStructurePort {
                override fun supports(
                    filename: String,
                    content: ByteArray,
                ): Boolean = true

                override fun extract(
                    filename: String,
                    content: ByteArray,
                ): PptxDeckDto = structureProvider()
            }
        val assembler = PptxPromptAssembler(fakePromptReader)
        return PptxAnalysisOrchestrator(
            structureExtractor = fakeStructure,
            jsonCodec = TestJsonCodec.create(),
            questionExtractor = questionExtractor,
            answerMapper = answerMapper,
            renderer = renderer,
            storage = storage,
            promptAssembler = assembler,
            analysisLog = PptxAnalysisLog(TestJsonCodec.create()),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )
    }

    private fun sampleDeck(): PptxDeckDto =
        PptxDeckDto(
            slideWidthPx = 1280,
            slideHeightPx = 720,
            theme = PptxThemeDto(emptyMap(), "Calibri", "Calibri", null),
            slides =
                listOf(
                    PptxSlideDto(
                        index = 1,
                        title = "Velocity",
                        backgroundColor = null,
                        shapes =
                            listOf(
                                PptxShapeDto.TextShape(
                                    xPx = 50,
                                    yPx = 50,
                                    widthPx = 600,
                                    heightPx = 60,
                                    textRuns =
                                        listOf(
                                            TextRunDto(
                                                text = "Wie war die Velocity in den letzten zwei Sprints?",
                                                fontFamily = "Calibri",
                                                fontSizePt = 24,
                                                bold = false,
                                                italic = false,
                                                colorHex = "#000000",
                                                bullet = false,
                                            ),
                                        ),
                                    placeholderType = "TITLE",
                                ),
                                PptxShapeDto.TextShape(
                                    xPx = 50,
                                    yPx = 130,
                                    widthPx = 600,
                                    heightPx = 200,
                                    textRuns =
                                        listOf(
                                            TextRunDto(
                                                text = "Antwort: Velocity war 42 Story-Points.",
                                                fontFamily = "Calibri",
                                                fontSizePt = 18,
                                                bold = false,
                                                italic = false,
                                                colorHex = "#000000",
                                                bullet = false,
                                            ),
                                        ),
                                    placeholderType = "BODY",
                                ),
                            ),
                        notes = null,
                        rawText = "Wie war die Velocity …",
                    ),
                ),
            sourceFileName = "test.pptx",
        )

    private fun newAnalysis(
        @TempDir tempDir: Path,
    ): ReviewAnalysis {
        val analysis =
            ReviewAnalysis(
                id = 1L,
                jiraSprintUrl = "https://jira.example.com/boards/1",
                jiraConfigId = 10L,
                status = "RUNNING",
            )
        storage.saveUpload(analysis.id, "test.pptx", byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        return analysis
    }

    private fun sprintInfo() =
        SprintInfo(
            id = 1L,
            name = "Sprint 42",
            state = "active",
            startDate = "2024-01-01",
            endDate = "2024-01-14",
            goal = "Ship MVP",
        )

    private fun issues() =
        listOf(
            SprintIssue(
                key = "PROJ-1",
                summary = "Login-Flow",
                issueType = "Story",
                status = "Done",
                priority = "High",
                assignee = "Alice",
                createdAt = null,
                startedAt = null,
                resolvedAt = null,
                qaLoopCount = 0,
                labels = emptyList(),
                description = "",
                url = "https://jira",
                comments = "",
            ),
        )

    @Test
    fun `runPptxAnalysis returns null when no pptx file is present`(
        @TempDir tempDir: Path,
    ) {
        setupDefaultPromptReader()
        val aiCaller =
            object : AiCaller {
                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutSec: Long,
                ): String = ""
            }
        val orch = orchestrator(aiCaller)
        val analysis =
            ReviewAnalysis(
                id = 999L,
                jiraSprintUrl = "https://jira",
                jiraConfigId = 1L,
                status = "RUNNING",
            )
        val path = orch.runPptxAnalysis(analysis, sprintInfo(), issues(), "de", "gpt-4", aiCaller)
        assertNull(path)
    }

    @Test
    fun `runPptxAnalysis happy path persists questions and answers`(
        @TempDir tempDir: Path,
    ) {
        setupDefaultPromptReader()
        val stage1Response =
            """
            {"slides":[{"slideIndex":1,"questions":[
              {"questionId":"s1-shape0","text":"Wie war die Velocity?"}
            ]}]}
            """.trimIndent()
        val stage2Response = "- s1-shape0: Velocity war 42 Story-Points, 8 % über Schnitt."
        val calls = intArrayOf(0, 0)
        val aiCaller =
            object : AiCaller {
                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutSec: Long,
                ): String =
                    if (prompt.startsWith("STAGE1")) {
                        calls[0]++
                        stage1Response
                    } else {
                        calls[1]++
                        stage2Response
                    }
            }
        val orch = orchestrator(aiCaller)
        val analysis = newAnalysis(tempDir)
        val path = orch.runPptxAnalysis(analysis, sprintInfo(), issues(), "de", "gpt-4", aiCaller)

        assertNotNull(path, "HTML path must be returned")
        assertEquals(1, calls[0], "Stage 1 must be called exactly once")
        assertEquals(1, calls[1], "Stage 2 must be called exactly once")
        assertNotNull(analysis.pptxQuestionsJson, "Stage 1 plan must be persisted")
        assertNotNull(analysis.pptxAnswersJson, "Stage 2 answers must be persisted")
        assertTrue(analysis.pptxQuestionsJson!!.contains("s1-shape0"))
        assertTrue(analysis.pptxAnswersJson!!.contains("Velocity war 42"))
        assertEquals(listOf("s1-shape0"), questionExtractor.parse(analysis.pptxQuestionsJson).questionIds().toList())
        assertEquals(path, analysis.resultHtmlPath)
        val html = java.io.File(path!!).readText(Charsets.UTF_8)
        assertTrue(html.contains("Velocity war 42 Story-Points"), "Expected real answer in HTML, got: $html")
        assertTrue(html.contains("pptx-answer"), "Expected answer CSS class in HTML")
    }

    @Test
    fun `runPptxAnalysis falls back to empty plan when stage1 fails`(
        @TempDir tempDir: Path,
    ) {
        setupDefaultPromptReader()
        val stage2Response = "(no questions to answer)"
        val aiCaller =
            object : AiCaller {
                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutSec: Long,
                ): String =
                    if (prompt.startsWith("STAGE1")) {
                        error("simulated stage-1 failure")
                    } else {
                        stage2Response
                    }
            }
        val orch = orchestrator(aiCaller)
        val analysis = newAnalysis(tempDir)
        val path = orch.runPptxAnalysis(analysis, sprintInfo(), issues(), "de", "gpt-4", aiCaller)
        assertNotNull(path, "Even with Stage-1 failure, HTML should still be rendered")
        assertNull(analysis.pptxQuestionsJson)
    }

    @Test
    fun `runPptxAnalysis falls back to empty answers when stage2 fails`(
        @TempDir tempDir: Path,
    ) {
        setupDefaultPromptReader()
        val stage1Response =
            """
            {"slides":[{"slideIndex":1,"questions":[
              {"questionId":"s1-shape0","text":"Velocity?"}
            ]}]}
            """.trimIndent()
        val aiCaller =
            object : AiCaller {
                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutSec: Long,
                ): String =
                    if (prompt.startsWith("STAGE1")) {
                        stage1Response
                    } else {
                        error("simulated stage-2 failure")
                    }
            }
        val orch = orchestrator(aiCaller)
        val analysis = newAnalysis(tempDir)
        val path = orch.runPptxAnalysis(analysis, sprintInfo(), issues(), "de", "gpt-4", aiCaller)
        assertNotNull(path, "Even with Stage-2 failure, HTML should still be rendered")
        assertNotNull(analysis.pptxQuestionsJson, "Stage 1 plan must be persisted")
        assertNotNull(analysis.pptxAnswersJson)
        assertTrue(analysis.pptxAnswersJson!!.contains("(KI: keine Daten gefunden)"))
    }

    @Test
    fun `runPptxAnalysis updates pptxSlideCount`() {
        setupDefaultPromptReader()
        val aiCaller =
            object : AiCaller {
                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutSec: Long,
                ): String = ""
            }
        val orch = orchestrator(aiCaller)
        val analysis =
            ReviewAnalysis(
                id = 2L,
                jiraSprintUrl = "https://jira",
                jiraConfigId = 1L,
                status = "RUNNING",
            )
        storage.saveUpload(analysis.id, "test.pptx", byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        orch.runPptxAnalysis(analysis, sprintInfo(), issues(), "de", "gpt-4", aiCaller)
        assertEquals(1, analysis.pptxSlideCount)
    }

    @Test
    fun `runPptxAnalysis prevents placeholder strings from leaking into HTML`() {
        setupDefaultPromptReader()
        val stage1Response =
            """
            {"slides":[{"slideIndex":1,"questions":[
              {"questionId":"s1-shape0","text":"Velocity?"}
            ]}]}
            """.trimIndent()
        val realAnswer = "Antwort = 42 Story-Points"
        val aiCaller =
            object : AiCaller {
                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutSec: Long,
                ): String = if (prompt.startsWith("STAGE1")) stage1Response else "- s1-shape0: $realAnswer"
            }
        val orch = orchestrator(aiCaller)
        val analysis =
            ReviewAnalysis(
                id = 3L,
                jiraSprintUrl = "https://jira",
                jiraConfigId = 1L,
                status = "RUNNING",
            )
        storage.saveUpload(analysis.id, "test.pptx", byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        val path = orch.runPptxAnalysis(analysis, sprintInfo(), issues(), "de", "gpt-4", aiCaller)
        val html = java.io.File(path!!).readText(Charsets.UTF_8)
        assertTrue(html.contains(realAnswer), "Real answer must be in HTML")
        assertTrue(
            !html.contains("KI-Antwort für:"),
            "Placeholder string must NOT appear in rendered HTML",
        )
    }

    @Test
    fun `runPptxAnalysis picks the named file and ignores zero-byte artefacts`(
        @TempDir tempDir: Path,
    ) {
        setupDefaultPromptReader()
        val aiCaller =
            object : AiCaller {
                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutSec: Long,
                ): String = ""
            }

        val previous = System.getProperty("stacklens.data-dir")
        System.setProperty("stacklens.data-dir", tempDir.toString())
        try {
            val isolatedStorage: com.stacklens.application.port.out.PptxStoragePort =
                com.stacklens.adapter.out.review.pptx
                    .PptxStorageAdapter()
            val isolatedOrch =
                orchestrator(aiCaller).let { base ->
                    PptxAnalysisOrchestrator(
                        structureExtractor =
                            object : PptxStructurePort {
                                override fun supports(
                                    filename: String,
                                    content: ByteArray,
                                ): Boolean = true

                                override fun extract(
                                    filename: String,
                                    content: ByteArray,
                                ): PptxDeckDto = sampleDeck()
                            },
                        jsonCodec = TestJsonCodec.create(),
                        questionExtractor = questionExtractor,
                        answerMapper = answerMapper,
                        renderer = renderer,
                        storage = isolatedStorage,
                        promptAssembler = PptxPromptAssembler(fakePromptReader),
                        analysisLog = PptxAnalysisLog(TestJsonCodec.create()),
                        clock = com.stacklens.adapter.out.time.SystemClockPort,
                    )
                }

            isolatedOrch

            val analysis =
                ReviewAnalysis(
                    id = 42L,
                    jiraSprintUrl = "https://jira",
                    jiraConfigId = 1L,
                    status = "RUNNING",
                    pptxFileName = "FulfillmentReviewsVorlage.pptx",
                )

            isolatedStorage.saveUpload(analysis.id, "test.pptx", byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            val realBytes = ByteArray(2048) { (it % 256).toByte() }
            isolatedStorage.saveUpload(analysis.id, "FulfillmentReviewsVorlage.pptx", realBytes)

            val found = isolatedStorage.findUploadedPptx(analysis.id, "FulfillmentReviewsVorlage.pptx")
            assertNotNull(found, "test set-up: real upload must be findable")
            val names =
                listOf(
                    isolatedStorage.findUploadedPptx(analysis.id, "test.pptx")?.name,
                    isolatedStorage.findUploadedPptx(analysis.id, "FulfillmentReviewsVorlage.pptx")?.name,
                ).filterNotNull()
            assertTrue(
                names.contains("original-test.pptx"),
                "test set-up: artefact must be present, got $names",
            )
            assertTrue(
                names.contains("original-FulfillmentReviewsVorlage.pptx"),
                "test set-up: real upload must be present, got $names",
            )

            val capturedBytes =
                java.util.concurrent.atomic
                    .AtomicReference<ByteArray?>(null)
            val capturingOrch =
                PptxAnalysisOrchestrator(
                    structureExtractor =
                        object : PptxStructurePort {
                            override fun supports(
                                filename: String,
                                content: ByteArray,
                            ): Boolean = true

                            override fun extract(
                                filename: String,
                                content: ByteArray,
                            ): PptxDeckDto {
                                capturedBytes.set(content.copyOf())
                                return sampleDeck()
                            }
                        },
                    jsonCodec = TestJsonCodec.create(),
                    questionExtractor = questionExtractor,
                    answerMapper = answerMapper,
                    renderer = renderer,
                    storage = isolatedStorage,
                    promptAssembler = PptxPromptAssembler(fakePromptReader),
                    analysisLog = PptxAnalysisLog(TestJsonCodec.create()),
                    clock = com.stacklens.adapter.out.time.SystemClockPort,
                )

            val crashingAi =
                object : AiCaller {
                    override fun execute(
                        model: String,
                        prompt: String,
                        timeoutSec: Long,
                    ): String = error("AI not exercised in this regression test")
                }
            runCatching {
                capturingOrch.runPptxAnalysis(analysis, sprintInfo(), issues(), "de", "gpt-4", crashingAi)
            }

            val extracted = capturedBytes.get()
            assertNotNull(
                extracted,
                "PptxStructurePort.extract was never called — the orchestrator returned before reaching the parser",
            )
            assertEquals(
                realBytes.size,
                extracted!!.size,
                "Apache POI must have been handed the real 2 KB upload, " +
                    "not the 4-byte test artefact (which is ${realBytes.size} bytes larger)",
            )
            assertTrue(
                extracted.contentEquals(realBytes),
                "Apache POI must have been handed the real upload's bytes verbatim",
            )
            assertEquals(
                1,
                analysis.pptxSlideCount,
                "pptxSlideCount must be set — proves the real file was parsed, not the 4-byte artefact",
            )
        } finally {
            if (previous == null) {
                System.clearProperty("stacklens.data-dir")
            } else {
                System.setProperty("stacklens.data-dir", previous)
            }
        }
    }
}
