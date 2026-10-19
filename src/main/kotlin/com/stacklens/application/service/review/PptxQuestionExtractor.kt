package com.stacklens.application.service.review

import com.stacklens.application.port.out.JsonTree
import com.stacklens.application.port.out.JsonTreeReaderPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.PptxQuestion
import com.stacklens.domain.model.PptxQuestionPlan
import com.stacklens.domain.model.PptxSlideQuestions

@ApplicationService
class PptxQuestionExtractor(
    private val jsonCodec: JsonTreeReaderPort,
) {
    fun parse(rawJson: String?): PptxQuestionPlan {
        if (rawJson.isNullOrBlank()) return EMPTY_PLAN
        val root = jsonCodec.decodeTree(rawJson)
        if (root !is JsonTree.ObjectNode) return EMPTY_PLAN
        val slidesArray = root.entries["slides"] as? JsonTree.ArrayNode ?: return EMPTY_PLAN

        val byId = LinkedHashMap<String, PptxQuestion>()
        for (slideNode in slidesArray.elements) {
            if (slideNode !is JsonTree.ObjectNode) continue
            val slideIndex = (slideNode.entries["slideIndex"] as? JsonTree.NumberNode)?.value?.toInt() ?: continue
            if (slideIndex < 0) continue
            val questions = slideNode.entries["questions"] as? JsonTree.ArrayNode ?: continue
            for (q in questions.elements) {
                if (q !is JsonTree.ObjectNode) continue
                val questionId = (q.entries["questionId"] as? JsonTree.TextNode)?.value?.trim()
                val text = (q.entries["text"] as? JsonTree.TextNode)?.value?.trim()
                if (questionId.isNullOrBlank() || text.isNullOrBlank()) continue
                if (!QUESTION_ID_PATTERN.matches(questionId)) continue
                val (sIdx, shapeIdx) = parseQuestionId(questionId) ?: continue
                if (sIdx != slideIndex) continue
                byId[questionId] =
                    PptxQuestion(
                        questionId = questionId,
                        slideIndex = sIdx,
                        shapeIndex = shapeIdx,
                        text = text,
                    )
            }
        }
        val grouped =
            byId.values
                .groupBy { it.slideIndex }
                .toSortedMap()
                .map { (slideIndex, qs) -> PptxSlideQuestions(slideIndex, qs.sortedBy { it.shapeIndex }) }
        return PptxQuestionPlan(grouped)
    }

    private fun parseQuestionId(id: String): Pair<Int, Int>? {
        val match = QUESTION_ID_PATTERN.matchEntire(id) ?: return null
        val (s, k) = match.destructured
        val sIdx = s.toIntOrNull() ?: return null
        val kIdx = k.toIntOrNull() ?: return null
        return sIdx to kIdx
    }

    companion object {
        private val EMPTY_PLAN = PptxQuestionPlan(emptyList())
        private val QUESTION_ID_PATTERN = Regex("^s(\\d+)-shape(\\d+)$")
    }
}
