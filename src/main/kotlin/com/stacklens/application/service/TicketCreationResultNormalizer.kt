package com.stacklens.application.service

import com.stacklens.application.service.ticketcreation.IssueTypeSectionStrategy
import com.stacklens.application.service.ticketcreation.TicketCreationDefaults
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.IssueType

@ApplicationService
class TicketCreationResultNormalizer(
    private val sectionStrategies: List<IssueTypeSectionStrategy>,
) {
    fun normalize(
        result: Map<String, Any?>,
        acceptanceLabel: String = SubtaskBodyFormatter.GERMAN_LABEL,
    ): Map<String, Any?> {
        val originalDescription = result["description"] as? String ?: ""
        val description = removeInternalSubtaskNote(originalDescription)
        val explicitSubtasks = explicitSubtasks(result["subtasks"])
        val subtasks =
            if (explicitSubtasks.isNotEmpty()) {
                explicitSubtasks
            } else {
                inferSubtasks(result, description)
            }

        return result +
            mapOf(
                "description" to description,
                "subtasks" to subtasks.map { withNormalisedBody(it, acceptanceLabel) },
            )
    }

    private fun withNormalisedBody(
        subtask: Map<*, *>,
        acceptanceLabel: String,
    ): Map<String, Any?> {
        val fields = subtask.entries.associate { (k, v) -> k.toString() to v }
        val body = fields["description"] as? String ?: return fields
        return fields + mapOf("description" to SubtaskBodyFormatter.format(body, acceptanceLabel))
    }

    internal fun inferSubtasks(
        result: Map<String, Any?>,
        description: String,
    ): List<Map<String, Any?>> {
        val issueType = result["issueType"] as? String ?: IssueType.DEFAULT.displayName
        val sourceSection =
            extractSection(description, IMPLEMENTATION_SECTION_NAMES)
                ?: run {
                    val strategy = sectionStrategies.firstOrNull { it.matches(issueType) }
                    strategy?.let { extractSection(description, it.sectionNames()) }
                }
                ?: return emptyList()
        val items = sourceSection.second.distinct().take(MAX_INFERRED_SUBTASKS)
        if (items.size < MIN_INFERRED_SUBTASKS) return emptyList()

        val priority = (result["priority"] as? String)?.ifBlank { TicketCreationDefaults.DEFAULT_PRIORITY } ?: TicketCreationDefaults.DEFAULT_PRIORITY
        val labels = (result["labels"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val parentSummary = (result["summary"] as? String).orEmpty()
        val parentContext = extractFirstSection(description, sourceSection.first)

        return items.map { item ->
            mapOf(
                "summary" to conciseSummary(item),
                "description" to buildSubtaskDescription(parentContext, parentSummary, sourceSection.first, item),
                "issueType" to TicketCreationDefaults.SUBTASK_ISSUE_TYPE,
                "priority" to priority,
                "labels" to labels,
            )
        }
    }

    internal fun removeInternalSubtaskNote(description: String): String {
        val lines = description.lines()
        val output = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            if (lines[index].trimStart().startsWith(INTERNAL_NOTE_PREFIX, ignoreCase = true)) {
                index++
                while (index < lines.size && lines[index].trimStart().startsWith(">")) index++
                if (index < lines.size && lines[index].isBlank()) index++
            } else {
                output += lines[index]
                index++
            }
        }
        return output.joinToString("\n").trimEnd()
    }

    private fun explicitSubtasks(raw: Any?): List<Map<*, *>> =
        (raw as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            .orEmpty()

    private fun extractSection(
        description: String,
        sectionNames: List<String>,
    ): Pair<String, List<String>>? {
        val lines = description.lines()
        var header: String? = null
        val items = mutableListOf<String>()

        for (line in lines) {
            val heading = HEADING_REGEX.matchEntire(line.trim())
            if (heading != null) {
                if (header != null) break
                val headingText = heading.groupValues[1]
                if (sectionNames.any { headingText.contains(it, ignoreCase = true) }) {
                    header = line.trim()
                }
                continue
            }
            if (header != null) {
                val bullet = BULLET_REGEX.matchEntire(line)
                if (bullet != null) {
                    val item = bullet.groupValues[1].trim()
                    if (item.isNotBlank()) items += item
                }
            }
        }

        return header?.let { it to items }
    }

    private fun extractFirstSection(
        description: String,
        sourceHeader: String,
    ): String {
        val lines = description.lines()
        val start =
            lines.indexOfFirst {
                HEADING_REGEX.matches(it.trim()) && it.trim() != sourceHeader
            }
        if (start < 0) return ""
        val context = mutableListOf(lines[start].trim())
        for (line in lines.drop(start + 1)) {
            if (HEADING_REGEX.matches(line.trim()) || line.trim() == "---") break
            if (!line.trimStart().startsWith(">")) context += line
        }
        return context.joinToString("\n").trim()
    }

    private fun buildSubtaskDescription(
        parentContext: String,
        parentSummary: String,
        sourceHeader: String,
        item: String,
    ): String {
        val context =
            parentContext.ifBlank {
                "## 🔗 Parent Ticket\n$parentSummary"
            }
        return "$context\n\n---\n\n$sourceHeader\n- $item\n\n---"
    }

    private fun conciseSummary(item: String): String =
        item
            .replace(Regex("[*_`]+"), "")
            .trim()
            .take(MAX_SUMMARY_LENGTH)

    companion object {
        const val INTERNAL_NOTE_PREFIX = "> **Note:** This is an in-description section"
        const val MIN_INFERRED_SUBTASKS = 2
        const val MAX_INFERRED_SUBTASKS = 5
        const val MAX_SUMMARY_LENGTH = 120

        val HEADING_REGEX = Regex("^##\\s+(.+)$")
        val BULLET_REGEX = Regex("^\\s*[-*]\\s+(?:\\[[ xX]\\]\\s*)?(.+?)\\s*$")
        val IMPLEMENTATION_SECTION_NAMES =
            listOf(
                "Steps / Sub-tasks",
                "Steps / Subtasks",
                "Schritte / Teilaufgaben",
                "Schritte / Unteraufgaben",
                "Implementation Plan",
                "Implementierungsplan",
            )
        val ACCEPTANCE_CRITERIA_SECTION_NAMES =
            listOf(
                "Acceptance Criteria",
                "Akzeptanzkriterien",
                "Abnahmekriterien",
            )
        val DEFINITION_OF_DONE_SECTION_NAMES = listOf("Definition of Done")
    }
}
