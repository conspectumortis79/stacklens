package com.stacklens.application.service.review

import com.stacklens.application.port.out.JsonWriterPort
import com.stacklens.application.port.out.PptxStoragePort
import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
import com.stacklens.application.port.out.review.PptxRenderPort
import com.stacklens.application.port.out.review.PptxStructurePort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.ai.AiCaller
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.PptxAnswerSet
import com.stacklens.domain.model.PptxDeckDto
import com.stacklens.domain.model.PptxQuestionPlan
import com.stacklens.domain.model.ReviewAnalysis
import org.slf4j.LoggerFactory

@ApplicationService
class PptxAnalysisOrchestrator(
    private val structureExtractor: PptxStructurePort,
    private val jsonCodec: JsonWriterPort,
    private val questionExtractor: PptxQuestionExtractor,
    private val answerMapper: PptxAnswerMapper,
    private val renderer: PptxRenderPort,
    private val storage: PptxStoragePort,
    private val promptAssembler: PptxPromptAssembler,
    private val analysisLog: PptxAnalysisLog,
    private val clock: ClockPort,
) {
    private val log = LoggerFactory.getLogger(PptxAnalysisOrchestrator::class.java)

    fun runPptxAnalysis(
        analysis: ReviewAnalysis,
        sprintInfo: SprintInfo?,
        issues: List<SprintIssue>,
        lang: String,
        model: String,
        aiCaller: AiCaller,
    ): String? {
        val expectedName = analysis.pptxFileName?.takeIf { it.isNotBlank() }
        val pptxFile = storage.findUploadedPptx(analysis.id, expectedName) ?: return null

        val deck: PptxDeckDto =
            try {
                structureExtractor.extract(pptxFile.name, storage.readUploadBytes(pptxFile))
            } catch (e: Exception) {
                log.warn("PPTX extraction failed for analysis {}: {}", analysis.id, e.message)
                addLog(analysis, "PPTX-Extraktion fehlgeschlagen: ${e.message}")
                return null
            }
        analysis.pptxSlideCount = deck.slides.size
        addLog(analysis, "PPTX-Struktur extrahiert: ${deck.slides.size} Folien")

        val questionPlan: PptxQuestionPlan =
            try {
                val stage1Prompt =
                    promptAssembler.buildStage1Prompt(
                        lang = lang,
                        sprintInfo = sprintInfo,
                        issues = issues,
                        deck = deck,
                    )
                val rawStage1 = aiCaller.execute(model, stage1Prompt, STAGE_TIMEOUT_SEC)
                val plan = questionExtractor.parse(rawStage1)
                analysis.pptxQuestionsJson = encodePlan(plan)
                plan
            } catch (e: Exception) {
                log.warn("Stage-1 AI call failed for analysis {}: {}", analysis.id, e.message)
                addLog(analysis, "Stage 1 fehlgeschlagen: ${e.message}")
                PptxQuestionPlan(emptyList())
            }
        addLog(
            analysis,
            "Stage 1: ${questionPlan.all().size} Frage(n) extrahiert",
        )

        val answerSet: PptxAnswerSet =
            try {
                val stage2Prompt =
                    promptAssembler.buildStage2Prompt(
                        lang = lang,
                        sprintInfo = sprintInfo,
                        issues = issues,
                        plan = questionPlan,
                        targetLang = lang,
                    )
                val rawStage2 = aiCaller.execute(model, stage2Prompt, STAGE_TIMEOUT_SEC)
                val parsed = answerMapper.parse(rawStage2, questionPlan)
                analysis.pptxAnswersJson = encodeAnswers(parsed)
                parsed
            } catch (e: Exception) {
                log.warn("Stage-2 AI call failed for analysis {}: {}", analysis.id, e.message)
                addLog(analysis, "Stage 2 fehlgeschlagen: ${e.message}")

                val placeholder =
                    questionPlan.all().associate { q ->
                        q.questionId to PLACEHOLDER_NO_DATA
                    }
                analysis.pptxAnswersJson = encodeAnswers(PptxAnswerSet(placeholder))
                PptxAnswerSet(placeholder)
            }
        addLog(analysis, "Stage 2: ${answerSet.answers.size} Antwort(en) generiert")

        val augmented =
            deck.copy(
                slides =
                    deck.slides.map { slide ->

                        val planned = questionPlan.slides.firstOrNull { it.slideIndex == slide.index }
                        slide.copy(
                            questionIds = planned?.questions?.map { it.questionId } ?: emptyList(),
                            answeredQuestions = answerSet.answers.filterKeys { it.startsWith("s${slide.index}-") },
                        )
                    },
            )
        val html = renderer.render(augmented)
        val path = storage.saveHtml(analysis.id, html)
        analysis.resultHtmlPath = path
        addLog(analysis, "HTML gespeichert: $path")
        return path
    }

    private fun encodePlan(plan: PptxQuestionPlan): String = jsonCodec.encode(plan)

    private fun encodeAnswers(answers: PptxAnswerSet): String = jsonCodec.encode(answers)

    private fun addLog(
        analysis: ReviewAnalysis,
        text: String,
    ) {
        val existing: List<String> =
            try {
                val current = analysis.processLogJson
                if (current.isNullOrBlank()) emptyList() else analysisLog.decode(current)
            } catch (_: Exception) {
                emptyList()
            }
        val updated = existing.toMutableList().apply { add("${clock.localDateTime()} $text") }
        analysis.processLogJson = analysisLog.encode(updated)
    }

    companion object {
        const val STAGE_TIMEOUT_SEC = 60L
        const val PLACEHOLDER_NO_DATA = "(KI: keine Daten gefunden)"
    }
}
