package com.stacklens.application.service.review

import com.stacklens.adapter.out.review.pptx.PptxSlideHtmlRenderer
import com.stacklens.adapter.out.review.pptx.PptxStructureExtractor
import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
import com.stacklens.application.port.out.prompt.PromptContext
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.ai.AiCaller
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.testsupport.TestJsonCodec
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.Color
import java.awt.Rectangle
import java.io.File
import java.nio.file.Path

class PptxRealWorldIntegrationTest {
    @Test
    fun `three Sprint-Review questions in a real pptx are answered and rendered in HTML`(
        @TempDir tempDir: Path,
    ) {
        val analysis =
            ReviewAnalysis(
                id = 100L,
                jiraSprintUrl = "https://jira.example.com/boards/1",
                jiraConfigId = 1L,
                status = "RUNNING",
            )
        val storage: com.stacklens.application.port.out.PptxStoragePort =
            com.stacklens.adapter.out.review.pptx
                .PptxStorageAdapter()
        val pptxFile = storage.saveUpload(analysis.id, "sprint-review-fixture.pptx", ByteArray(0))

        val builder = TestPptxBuilder()
        pptxFile.writeBytes(
            builder.buildDeck(
                listOf(
                    "Wie war die Velocity im Sprint 42?",
                    "Welche Risiken und Blocker sind aufgetreten?",
                    "Was sind die Top-3 Action Items für den nächsten Sprint?",
                ),
            ),
        )

        val sprintInfo =
            SprintInfo(
                id = 1L,
                name = "Sprint 42",
                state = "active",
                startDate = "2024-01-01",
                endDate = "2024-01-14",
                goal = "MVP-Ready",
            )
        val issues =
            listOf(
                SprintIssue(
                    key = "PROJ-1",
                    summary = "Login-Flow Production Cutover",
                    issueType = "Story",
                    status = "Done",
                    priority = "High",
                    assignee = "Alice",
                    createdAt = "2024-01-02",
                    startedAt = "2024-01-03",
                    resolvedAt = "2024-01-10",
                    qaLoopCount = 0,
                    labels = listOf("backend"),
                    description = "Login-Flow in Produktion ausgerollt.",
                    url = "https://jira/PROJ-1",
                    comments = "Reibungsloser Rollout, CEO-Feedback positiv.",
                ),
                SprintIssue(
                    key = "PROJ-2",
                    summary = "Payment-Provider 504 sporadisch",
                    issueType = "Bug",
                    status = "Blocked",
                    priority = "Critical",
                    assignee = "Bob",
                    createdAt = "2024-01-04",
                    startedAt = "2024-01-05",
                    resolvedAt = null,
                    qaLoopCount = 2,
                    labels = listOf("payment"),
                    description = "Stripe antwortet sporadisch mit 504.",
                    url = "https://jira/PROJ-2",
                    comments = "Vendor in Kontakt, Patch angek\u00f6ndigt.",
                ),
                SprintIssue(
                    key = "PROJ-3",
                    summary = "Webhook-Doppelzustellung",
                    issueType = "Bug",
                    status = "In Progress",
                    priority = "High",
                    assignee = "Carol",
                    createdAt = "2024-01-06",
                    startedAt = "2024-01-07",
                    resolvedAt = null,
                    qaLoopCount = 1,
                    labels = listOf("notifications"),
                    description = "SendGrid-Retry produziert Doppelzustellung.",
                    url = "https://jira/PROJ-3",
                    comments = "Idempotenz-Key-Layer geplant.",
                ),
            )

        val aiCaller =
            object : AiCaller {
                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutSec: Long,
                ): String {
                    if (prompt.contains("STAGE1")) {
                        return """{"slides":[
                        {"slideIndex":1,"questions":[{"questionId":"s1-shape1","text":"Wie war die Velocity im Sprint 42?"}]},
                        {"slideIndex":2,"questions":[{"questionId":"s2-shape1","text":"Welche Risiken und Blocker sind aufgetreten?"}]},
                        {"slideIndex":3,"questions":[{"questionId":"s3-shape1","text":"Was sind die Top-3 Action Items für den nächsten Sprint?"}]}
                    ]}"""
                    }
                    return """
                        - s1-shape1: Velocity war 28 Story-Points (Sprint geplant: 30), 93 % Completion-Rate, alle 3 Mitglieder (Alice, Bob, Carol) aktiv.
                        - s2-shape1: Top-Risiko ist PROJ-2 (Payment-Provider 504, Critical, Blocked seit 5 Tagen). Daneben PROJ-3 (Webhook-Doppelzustellung, High, In Progress) und PROJ-1 (Dependencies zu Vendor).
                        - s3-shape1: 1) PROJ-2 unblocken (Owner: Bob). 2) Webhook-Idempotenz produktiv rollouten (Owner: Carol). 3) Login-Flow Post-Launch-Monitoring (Owner: Alice).
                        """.trimIndent()
                }
            }

        val promptReader: PromptReaderPort = mock()
        whenever(promptReader.context(any())).thenAnswer {
            PromptContext(
                mapOf(
                    "review.pptxStage1Prompt" to "STAGE1 {sprintData} {deckStructure}",
                    "review.pptxStage2Prompt" to "STAGE2 {sprintData} {questionPlan} {pptxLangDirective}",
                    "review.pptxLangDirective" to "Heuristik: target={targetLang}",
                ),
            )
        }
        whenever(promptReader.get(any(), any(), any())).thenAnswer { "[${it.arguments[1]}]" }

        val orchestrator =
            PptxAnalysisOrchestrator(
                structureExtractor = PptxStructureExtractor(),
                jsonCodec = TestJsonCodec.create(),
                questionExtractor = PptxQuestionExtractor(TestJsonCodec.create()),
                answerMapper = PptxAnswerMapper(TestJsonCodec.create()),
                renderer = PptxSlideHtmlRenderer(),
                storage = storage,
                promptAssembler = PptxPromptAssembler(promptReader),
                analysisLog = PptxAnalysisLog(TestJsonCodec.create()),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )

        val path =
            orchestrator.runPptxAnalysis(
                analysis = analysis,
                sprintInfo = sprintInfo,
                issues = issues,
                lang = "de",
                model = "gpt-4o-mini",
                aiCaller = aiCaller,
            )
        assertNotNull(path, "HTML path must be returned for real pptx pipeline")

        assertEquals(3, analysis.pptxSlideCount, "Slide count must match the source PPTX")
        assertNotNull(analysis.pptxQuestionsJson, "Stage 1 plan must be persisted")
        assertNotNull(analysis.pptxAnswersJson, "Stage 2 answers must be persisted")
        assertTrue(analysis.pptxQuestionsJson!!.contains("s1-shape1"))
        assertTrue(analysis.pptxQuestionsJson!!.contains("s2-shape1"))
        assertTrue(analysis.pptxQuestionsJson!!.contains("s3-shape1"))
        assertEquals(path, analysis.resultHtmlPath)

        val html = File(path!!).readText(Charsets.UTF_8)
        assertTrue(html.contains("Wie war die Velocity im Sprint 42?"), "Slides must contain their question text")
        assertTrue(html.contains("Welche Risiken und Blocker sind aufgetreten?"))
        assertTrue(html.contains("Top-3 Action Items"))
        assertTrue(html.contains("Velocity war 28 Story-Points"), "First answer must be in HTML")
        assertTrue(html.contains("PROJ-2"), "Second answer must reference PROJ-2 — the real critical blocker")
        assertTrue(html.contains("PROJ-2 unblocken"), "Third answer must mention the Top-1 action item")
        val answerCount = "class=\"pptx-run pptx-answer\"".toRegex().findAll(html).count()
        assertTrue(answerCount >= 3, "Expected at least 3 pptx-answer spans, got $answerCount")
        assertTrue(html.contains("pptx-answer-anchor"), "Answer anchors must be rendered")
        assertFalse(html.contains("KI-Antwort für:"), "No placeholder strings must leak into the HTML")
        assertTrue(
            html.contains("aspect-ratio:") && html.contains("data-slide-index"),
            "Slide aspect ratio + slide indexing must be present in the HTML",
        )
        val slideMatches = "data-slide-index=\"[0-9]+\"".toRegex().findAll(html).count()
        assertEquals(3, slideMatches, "Expected exactly 3 slide sections in the HTML")
    }

    @Test
    fun `empty pptx produces a valid html without crashes`(
        @TempDir tempDir: Path,
    ) {
        val analysis = ReviewAnalysis(id = 200L, jiraSprintUrl = "u", jiraConfigId = 1L, status = "RUNNING")
        val storage: com.stacklens.application.port.out.PptxStoragePort =
            com.stacklens.adapter.out.review.pptx
                .PptxStorageAdapter()
        val pptxFile = storage.saveUpload(analysis.id, "empty.pptx", ByteArray(0))
        val builder = TestPptxBuilder()
        pptxFile.writeBytes(builder.buildDeck(emptyList()))

        val promptReader: PromptReaderPort = mock()
        whenever(promptReader.context(any())).thenAnswer {
            PromptContext(
                mapOf(
                    "review.pptxStage1Prompt" to "STAGE1 {sprintData} {deckStructure}",
                    "review.pptxStage2Prompt" to "STAGE2 {sprintData} {questionPlan} {pptxLangDirective}",
                    "review.pptxLangDirective" to "Heuristik: target={targetLang}",
                ),
            )
        }
        whenever(promptReader.get(any(), any(), any())).thenAnswer { "[${it.arguments[1]}]" }

        val aiCaller =
            object : AiCaller {
                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutSec: Long,
                ): String = if (prompt.contains("STAGE1")) "{\"slides\":[]}" else ""
            }

        val orchestrator =
            PptxAnalysisOrchestrator(
                structureExtractor = PptxStructureExtractor(),
                jsonCodec = TestJsonCodec.create(),
                questionExtractor = PptxQuestionExtractor(TestJsonCodec.create()),
                answerMapper = PptxAnswerMapper(TestJsonCodec.create()),
                renderer = PptxSlideHtmlRenderer(),
                storage = storage,
                promptAssembler = PptxPromptAssembler(promptReader),
                analysisLog = PptxAnalysisLog(TestJsonCodec.create()),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )

        val path = orchestrator.runPptxAnalysis(analysis, null, emptyList(), "de", "gpt-4", aiCaller)
        assertNotNull(path)
        assertEquals(0, analysis.pptxSlideCount)
        val html = File(path!!).readText(Charsets.UTF_8)
        assertTrue(html.contains("<!DOCTYPE html>"))
    }
}

private class TestPptxBuilder {
    fun buildDeck(questions: List<String>): ByteArray {
        val show = XMLSlideShow()
        try {
            questions.forEachIndexed { idx, question ->
                val slide = show.createSlide()
                val title = slide.createTextBox()
                title.text = "Folie ${idx + 1}"
                title.setAnchor(Rectangle(50, 50, 800, 60))
                applyRun(title, fontSize = 24.0, bold = true, color = Color.BLACK)

                val body = slide.createTextBox()
                body.text = question
                body.setAnchor(Rectangle(50, 200, 800, 100))
                applyRun(body, fontSize = 18.0, bold = false, color = Color.DARK_GRAY)
            }
            val out = java.io.ByteArrayOutputStream()
            show.write(out)
            return out.toByteArray()
        } finally {
            show.close()
        }
    }

    private fun applyRun(
        textBox: org.apache.poi.xslf.usermodel.XSLFTextBox,
        fontSize: Double,
        bold: Boolean,
        color: Color,
    ) {
        val paragraph = textBox.textParagraphs.first()
        val run = paragraph.textRuns.first()
        run.fontSize = fontSize
        run.isBold = bold
        run.setFontColor(color)
    }
}
