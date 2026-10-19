package com.stacklens.application.service.review

import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.PptxDeckDto
import com.stacklens.domain.model.PptxQuestionPlan
import com.stacklens.domain.model.PptxShapeDto
import com.stacklens.domain.model.PptxSlideDto

@ApplicationService
class PptxPromptAssembler(
    private val promptReader: PromptReaderPort,
) {
    fun buildStage1Prompt(
        lang: String,
        sprintInfo: SprintInfo?,
        issues: List<SprintIssue>,
        deck: PptxDeckDto,
    ): String {
        val ctx = promptReader.context(lang)
        val sprintData = renderSprintData(sprintInfo, issues)
        val deckStructure = renderDeckStructure(deck)
        return ctx.get("review.pptxStage1Prompt", mapOf("sprintData" to sprintData, "deckStructure" to deckStructure))
    }

    fun buildStage2Prompt(
        lang: String,
        sprintInfo: SprintInfo?,
        issues: List<SprintIssue>,
        plan: PptxQuestionPlan,
        targetLang: String,
    ): String {
        val ctx = promptReader.context(lang)
        val sprintData = renderSprintData(sprintInfo, issues)
        val questionPlan = renderQuestionPlan(plan)
        val pptxLangDirective =
            ctx.get(
                "review.pptxLangDirective",
                mapOf("targetLang" to targetLang),
            )
        return ctx.get(
            "review.pptxStage2Prompt",
            mapOf(
                "sprintData" to sprintData,
                "questionPlan" to questionPlan,
                "pptxLangDirective" to pptxLangDirective,
            ),
        )
    }

    private fun renderSprintData(
        sprintInfo: SprintInfo?,
        issues: List<SprintIssue>,
    ): String =
        buildString {
            if (sprintInfo != null) {
                appendLine("=== SPRINT ===")
                appendLine("Name: ${sprintInfo.name}")
                appendLine("State: ${sprintInfo.state}")
                appendLine("Period: ${sprintInfo.startDate ?: "?"} .. ${sprintInfo.endDate ?: "?"}")
                appendLine("Goal: ${sprintInfo.goal ?: "(none)"}")
                appendLine()
            }
            appendLine("=== TICKETS (${issues.size}) ===")
            issues.forEach { t ->
                appendLine(
                    "- ${t.key} [${t.issueType}/${t.status}/${t.priority ?: "?"}] " +
                        "Assignee=${t.assignee ?: "?"} | ${t.summary}",
                )
            }
        }

    private fun renderDeckStructure(deck: PptxDeckDto): String =
        buildString {
            appendLine("=== PPTX STRUCTURE (${deck.slides.size} slides, ${deck.slideWidthPx}x${deck.slideHeightPx}px) ===")
            deck.slides.forEach { slide ->
                appendLine()
                appendLine("--- Slide ${slide.index} (title: ${slide.title ?: "(none)"}) ---")
                renderSlide(slide, "    ")
            }
        }

    private fun StringBuilder.renderSlide(
        slide: PptxSlideDto,
        indent: String,
    ) {
        slide.shapes.forEachIndexed { idx, shape ->
            when (shape) {
                is PptxShapeDto.TextShape -> {
                    val text = shape.textRuns.joinToString("|") { it.text }.trim()
                    if (text.isNotBlank()) {
                        appendLine("${indent}shape$idx [TextShape]: $text")
                    }
                }
                is PptxShapeDto.PictureShape -> {
                    appendLine("${indent}shape$idx [Picture]")
                }
                is PptxShapeDto.TableShape -> {
                    appendLine("${indent}shape$idx [Table ${shape.rows.size}x${shape.rows.firstOrNull()?.size ?: 0}]")
                }
                is PptxShapeDto.AutoShape -> {
                    appendLine("${indent}shape$idx [AutoShape ${shape.shapeType}]")
                }
                is PptxShapeDto.FallbackImageShape -> {
                    appendLine("${indent}shape$idx [FallbackImage]")
                }
            }
        }
    }

    private fun renderQuestionPlan(plan: PptxQuestionPlan): String =
        buildString {
            if (plan.isEmpty()) {
                appendLine("(no questions identified in Stage 1)")
                return@buildString
            }
            appendLine("=== QUESTIONS (${plan.all().size}) ===")
            plan.slides.forEach { slide ->
                appendLine("Slide ${slide.slideIndex}:")
                slide.questions.forEach { q ->
                    appendLine("  - ${q.questionId}: ${q.text}")
                }
            }
        }
}
