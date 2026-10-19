package com.stacklens.application.service.review

import com.stacklens.application.port.out.JsonTree
import com.stacklens.application.port.out.JsonTreeReaderPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.PptxAnswerSet
import com.stacklens.domain.model.PptxQuestionPlan
import org.slf4j.LoggerFactory

@ApplicationService
class PptxAnswerMapper(
    private val jsonCodec: JsonTreeReaderPort,
) {
    private val log = LoggerFactory.getLogger(PptxAnswerMapper::class.java)

    fun parse(
        rawAnswer: String?,
        plan: PptxQuestionPlan,
    ): PptxAnswerSet {
        if (rawAnswer.isNullOrBlank()) return PptxAnswerSet.EMPTY
        val candidates = parseCandidates(rawAnswer)
        val allowedIds = plan.questionIds()
        if (allowedIds.isEmpty()) return PptxAnswerSet.EMPTY
        val filtered =
            candidates.filterKeys { id ->
                if (id in allowedIds) {
                    true
                } else {
                    log.warn("Stage-2 answer references unknown questionId '{}' — discarding", id)
                    false
                }
            }
        return PptxAnswerSet(filtered)
    }

    private fun parseCandidates(raw: String): Map<String, String> {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("{")) parseJson(trimmed) else parseMarkdown(trimmed)
    }

    private fun parseJson(raw: String): Map<String, String> {
        val root = jsonCodec.decodeTree(raw)
        if (root !is JsonTree.ObjectNode) return parseMarkdown(raw)
        val out = LinkedHashMap<String, String>()
        for ((key, value) in root.entries) {
            val id = key.trim()
            val text = value.asAnswerText()?.trim()
            if (text == null) {
                log.warn("Stage-2 answer for '{}' is not a scalar JSON value — discarded", key)
            } else if (id.isNotBlank() && text.isNotBlank() && QUESTION_ID_PATTERN.matches(id)) {
                out[id] = text
            }
        }
        return out
    }

    private fun JsonTree.asAnswerText(): String? =
        when (this) {
            is JsonTree.TextNode -> value
            is JsonTree.NumberNode -> value.toString()
            is JsonTree.BooleanNode -> value.toString()
            else -> null
        }

    private fun parseMarkdown(raw: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val pattern =
            Regex(
                """^[\s>]*([-*+]\s+)?\*{0,2}(s\d+-shape\d+)\*{0,2}\s*[:\-]\s*(.+?)$""",
            )
        raw
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val match = pattern.matchEntire(line) ?: return@forEach
                val (_, id, answer) = match.destructured
                out[id] = answer.trim()
            }
        return out
    }

    companion object {
        private val QUESTION_ID_PATTERN = Regex("^s\\d+-shape\\d+$")
    }
}
