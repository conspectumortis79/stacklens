package com.stacklens.application.service.review

import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
import com.stacklens.application.port.out.prompt.PromptContext
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.PptxDeckDto
import com.stacklens.domain.model.PptxQuestion
import com.stacklens.domain.model.PptxQuestionPlan
import com.stacklens.domain.model.PptxShapeDto
import com.stacklens.domain.model.PptxSlideDto
import com.stacklens.domain.model.PptxSlideQuestions
import com.stacklens.domain.model.PptxThemeDto
import com.stacklens.domain.model.TextRunDto
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PptxPromptAssemblerRenderingTest {
    private val promptReader: PromptReaderPort = mock()
    private val assembler = PptxPromptAssembler(promptReader)

    private fun stubContext() {
        whenever(promptReader.context(any<String>()))
            .thenReturn(
                PromptContext(
                    mapOf(
                        "review.pptxStage1Prompt" to "SPRINT[{sprintData}]DECK[{deckStructure}]",
                        "review.pptxStage2Prompt" to "SPRINT[{sprintData}]PLAN[{questionPlan}]LANG[{pptxLangDirective}]",
                        "review.pptxLangDirective" to "answer in {targetLang}",
                    ),
                ),
            )
    }

    private fun run(
        text: String,
        bold: Boolean = false,
    ): TextRunDto = TextRunDto(text = text, fontFamily = null, fontSizePt = null, bold = bold, italic = false, colorHex = null, bullet = false)

    private fun slide(
        index: Int,
        title: String?,
        shapes: List<PptxShapeDto>,
    ): PptxSlideDto =
        PptxSlideDto(
            index = index,
            title = title,
            backgroundColor = null,
            shapes = shapes,
            notes = null,
            rawText = "",
        )

    private fun deck(slides: List<PptxSlideDto>): PptxDeckDto =
        PptxDeckDto(
            slideWidthPx = 1280,
            slideHeightPx = 720,
            theme = PptxThemeDto(colors = emptyMap(), headingFont = "Arial", bodyFont = "Arial", backgroundColor = null),
            slides = slides,
            sourceFileName = "deck.pptx",
        )

    private fun issue(
        key: String,
        priority: String?,
        assignee: String?,
    ): SprintIssue =
        SprintIssue(
            key = key,
            summary = "summary of $key",
            description = "desc",
            issueType = "Story",
            status = "In Progress",
            priority = priority,
            assignee = assignee,
            labels = emptyList(),
            comments = "",
            url = "https://jira.test/browse/$key",
        )

    @Test fun `stage one renders sprint block dates and both unknown placeholders`() {
        stubContext()
        val sprint = SprintInfo(id = 1L, name = "Sprint 42", state = "ACTIVE", startDate = null, endDate = null, goal = null)

        val prompt = assembler.buildStage1Prompt("de", sprint, listOf(issue("PROJ-1", null, null), issue("PROJ-2", "High", "Alice")), deck(emptyList()))

        assertTrue(prompt.contains("Name: Sprint 42"))
        assertTrue(prompt.contains("Period: ? .. ?"))
        assertTrue(prompt.contains("Goal: (none)"))
        assertTrue(prompt.contains("=== TICKETS (2) ==="))
        assertTrue(prompt.contains("- PROJ-1 [Story/In Progress/?] Assignee=? | summary of PROJ-1"))
        assertTrue(prompt.contains("- PROJ-2 [Story/In Progress/High] Assignee=Alice | summary of PROJ-2"))
        assertTrue(prompt.contains("=== PPTX STRUCTURE (0 slides, 1280x720px) ==="))
    }

    @Test fun `stage one skips the sprint block when no sprint info is available`() {
        stubContext()
        val sprint = SprintInfo(id = 1L, name = "Sprint 42", state = "CLOSED", startDate = "2026-08-01", endDate = "2026-08-14", goal = "stabilise")

        val prompt = assembler.buildStage1Prompt("de", null, emptyList(), deck(emptyList()))
        val withSprint = assembler.buildStage1Prompt("de", sprint, emptyList(), deck(emptyList()))

        assertFalse(prompt.contains("=== SPRINT ==="))
        assertTrue(withSprint.contains("Period: 2026-08-01 .. 2026-08-14"))
        assertTrue(withSprint.contains("Goal: stabilise"))
        assertTrue(withSprint.contains("=== TICKETS (0) ==="))
    }

    @Test fun `every shape kind is rendered with its own marker`() {
        stubContext()
        val shapes =
            listOf(
                PptxShapeDto.TextShape(0, 0, 1, 1, listOf(run("Titel"), run("x")), null),
                PptxShapeDto.TextShape(0, 0, 1, 1, listOf(run("   ")), null),
                PptxShapeDto.PictureShape(0, 0, 1, 1, "data:image/png;base64,", "image/png"),
                PptxShapeDto.TableShape(0, 0, 1, 1, listOf(listOf(run("zelle")), listOf(run("a"), run("b")))),
                PptxShapeDto.TableShape(0, 0, 1, 1, emptyList()),
                PptxShapeDto.AutoShape(0, 0, 1, 1, fillColor = null, strokeColor = null, shapeType = "roundRect"),
                PptxShapeDto.FallbackImageShape(0, 0, 1, 1, "data:image/png;base64,"),
            )

        val prompt =
            assembler.buildStage1Prompt(
                "de",
                null,
                emptyList(),
                deck(listOf(slide(1, "Folientitel", shapes), slide(2, null, emptyList()))),
            )

        assertTrue(prompt.contains("shape0 [TextShape]: Titel|x"))
        assertFalse(prompt.contains("shape1 [TextShape]"))
        assertTrue(prompt.contains("shape2 [Picture]"))
        assertTrue(prompt.contains("shape3 [Table 2x1]"))
        assertTrue(prompt.contains("shape4 [Table 0x0]"))
        assertTrue(prompt.contains("shape5 [AutoShape roundRect]"))
        assertTrue(prompt.contains("shape6 [FallbackImage]"))
        assertTrue(prompt.contains("--- Slide 1 (title: Folientitel) ---"))
        assertTrue(prompt.contains("--- Slide 2 (title: (none)) ---"))
    }

    @Test fun `stage two renders the question plan and the language directive`() {
        stubContext()
        val plan =
            PptxQuestionPlan(
                slides =
                    listOf(
                        PptxSlideQuestions(
                            slideIndex = 1,
                            questions = listOf(PptxQuestion(questionId = "s1-shape0", slideIndex = 1, shapeIndex = 0, text = "Wie war die Velocity?")),
                        ),
                        PptxSlideQuestions(slideIndex = 2, questions = emptyList()),
                    ),
            )

        val prompt = assembler.buildStage2Prompt("en", null, emptyList(), plan, "Deutsch")

        assertTrue(prompt.contains("=== QUESTIONS (1) ==="))
        assertTrue(prompt.contains("Slide 1:"))
        assertTrue(prompt.contains("- s1-shape0: Wie war die Velocity?"))
        assertTrue(prompt.contains("answer in Deutsch"))
    }

    @Test fun `stage two reports an empty question plan`() {
        stubContext()

        val prompt = assembler.buildStage2Prompt("en", null, emptyList(), PptxQuestionPlan(slides = emptyList()), "English")

        assertTrue(prompt.contains("(no questions identified in Stage 1)"))
        assertFalse(prompt.contains("=== QUESTIONS"))
    }
}
