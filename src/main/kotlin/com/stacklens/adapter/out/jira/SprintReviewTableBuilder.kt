package com.stacklens.adapter.out.jira

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.SprintReviewRow
import com.stacklens.domain.model.SprintReviewState
import org.springframework.stereotype.Component

@Component
class SprintReviewTableBuilder(
    private val i18n: I18nPort,
    private val copilot: CopilotPort,
) {
    fun buildAdfDocument(
        rows: List<SprintReviewRow>,
        includeImprovement: Boolean,
        sprintName: String,
    ): Map<String, Any?> {
        val headingText =
            t("table_heading").let { base ->
                if (sprintName.isNotBlank()) "$base — $sprintName" else base
            }
        val tableContent = buildTable(rows, includeImprovement)
        return mapOf(
            "type" to "doc",
            "version" to 1,
            "content" to
                listOf<Map<String, Any?>>(
                    mapOf(
                        "type" to "heading",
                        "attrs" to mapOf("level" to 2),
                        "content" to listOf(mapOf("type" to "text", "text" to headingText)),
                    ),
                    mapOf("type" to "rule"),
                    tableContent,
                    mapOf("type" to "rule"),
                    mapOf(
                        "type" to "paragraph",
                        "content" to
                            listOf(
                                mapOf(
                                    "type" to "text",
                                    "text" to t("table_legend"),
                                    "marks" to listOf(mapOf("type" to "em")),
                                ),
                            ),
                    ),
                ),
        )
    }

    private fun buildTable(
        rows: List<SprintReviewRow>,
        includeImprovement: Boolean,
    ): Map<String, Any?> {
        val header =
            listOf(
                buildCell(t("col_ticket"), header = true),
                buildCell(t("col_status"), header = true),
            )
        val body =
            rows.map { row ->
                listOf(buildTicketCell(row), buildStatusCell(row, includeImprovement))
            }
        return mapOf(
            "type" to "table",
            "attrs" to mapOf("isNumberColumnEnabled" to false, "layout" to "default"),
            "content" to
                listOf(
                    mapOf(
                        "type" to "tableRow",
                        "content" to header,
                    ),
                ) +
                body.map { cells ->
                    mapOf("type" to "tableRow", "content" to cells)
                },
        )
    }

    private fun buildTicketCell(row: SprintReviewRow): Map<String, Any?> {
        val keyText =
            mapOf(
                "type" to "text",
                "text" to row.ticketKey,
                "marks" to
                    listOf(
                        mapOf(
                            "type" to "link",
                            "attrs" to mapOf("href" to row.ticketUrl, "title" to row.ticketSummary),
                        ),
                        mapOf("type" to "strong"),
                    ),
            )
        val summaryText =
            mapOf(
                "type" to "text",
                "text" to row.ticketSummary,
                "marks" to listOf(mapOf("type" to "em")),
            )
        val metaLine =
            if (row.issueType.isNotBlank() || row.assignee != null) {
                val meta = listOfNotNull(row.issueType.takeIf { it.isNotBlank() }, row.assignee?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (meta.isBlank()) {
                    emptyList()
                } else {
                    listOf(
                        mapOf(
                            "type" to "text",
                            "text" to meta,
                            "marks" to listOf(mapOf("type" to "em")),
                        ),
                    )
                }
            } else {
                emptyList()
            }
        val paragraphContent =
            buildList {
                add(keyText)
                add(mapOf("type" to "hardBreak"))
                add(summaryText)
                if (metaLine.isNotEmpty()) {
                    add(mapOf("type" to "hardBreak"))
                    addAll(metaLine)
                }
            }
        return mapOf(
            "type" to "tableCell",
            "attrs" to mapOf("colspan" to 1, "rowspan" to 1),
            "content" to
                listOf(
                    mapOf("type" to "paragraph", "content" to paragraphContent),
                ),
        )
    }

    private fun buildStatusCell(
        row: SprintReviewRow,
        includeImprovement: Boolean,
    ): Map<String, Any?> {
        val headerParagraph = buildStatusHeader(row)
        val bodyParagraphs = buildStatusBody(row, includeImprovement)
        return mapOf(
            "type" to "tableCell",
            "attrs" to mapOf("colspan" to 1, "rowspan" to 1),
            "content" to listOf(headerParagraph) + bodyParagraphs,
        )
    }

    private fun buildStatusHeader(row: SprintReviewRow): Map<String, Any?> {
        val pill = statusPill(row.state)
        val labelText = t("state_${row.state.name.lowercase()}")
        val headerContent: List<Map<String, Any?>> =
            listOf(
                mapOf(
                    "type" to "text",
                    "text" to "$pill  ",
                    "marks" to listOf(mapOf("type" to "strong")),
                ),
                mapOf(
                    "type" to "text",
                    "text" to labelText,
                    "marks" to listOf(mapOf("type" to "strong")),
                ),
            )
        return mapOf("type" to "paragraph", "content" to headerContent)
    }

    private fun buildStatusBody(
        row: SprintReviewRow,
        includeImprovement: Boolean,
    ): List<Map<String, Any?>> {
        val bodyParagraphs = mutableListOf<Map<String, Any?>>()
        bodyParagraphs += buildStoryPointsParagraph(row)
        if (includeImprovement) {
            bodyParagraphs += stateSpecificParagraphs(row)
        }
        return bodyParagraphs
    }

    private fun buildStoryPointsParagraph(row: SprintReviewRow): Map<String, Any?> {
        val spText =
            when (row.storyPoints) {
                null -> t("meta_no_story_points")
                else -> t("meta_story_points", row.storyPoints.toString())
            }
        return mapOf(
            "type" to "paragraph",
            "content" to listOf(mapOf("type" to "text", "text" to spText, "marks" to listOf(mapOf("type" to "em")))),
        )
    }

    private fun stateSpecificParagraphs(row: SprintReviewRow): List<Map<String, Any?>> =
        when (row.state) {
            SprintReviewState.BEARBEITET -> bearbeitetParagraphs(row)
            SprintReviewState.OFFENE_FRAGE -> offeneFrageParagraphs(row)
            SprintReviewState.REFINEMENT -> refinementParagraphs(row)
            SprintReviewState.FAILED -> failedParagraphs(row)
        }

    private fun bearbeitetParagraphs(row: SprintReviewRow): List<Map<String, Any?>> =
        if (row.improvementSummary.isNotBlank()) {
            listOf(textParagraph(row.improvementSummary))
        } else {
            emptyList()
        }

    private fun offeneFrageParagraphs(row: SprintReviewRow): List<Map<String, Any?>> {
        val out = mutableListOf<Map<String, Any?>>()
        if (row.improvementSummary.isNotBlank()) {
            out += textParagraph(row.improvementSummary)
        }
        if (row.openPoints.isNotEmpty()) {
            out += strongTextParagraph(t("open_points_label"))
            out += bulletListParagraphs(row.openPoints)
        }
        return out
    }

    private fun refinementParagraphs(row: SprintReviewRow): List<Map<String, Any?>> {
        val reasons = row.refinementReasons
        return if (reasons.isNotEmpty()) {
            listOf(
                strongTextParagraph(t("refinement_reasons_label")),
                bulletListParagraphs(reasons.map { translateReason(it) }),
            )
        } else {
            listOf(textParagraph(t("refinement_hint")))
        }
    }

    private fun failedParagraphs(row: SprintReviewRow): List<Map<String, Any?>> {
        val message = row.errorMessage?.takeIf { it.isNotBlank() } ?: t("failed_hint")
        return listOf(strongTextParagraph(message))
    }

    private fun textParagraph(text: String): Map<String, Any?> = mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to text)))

    private fun strongTextParagraph(text: String): Map<String, Any?> =
        mapOf(
            "type" to "paragraph",
            "content" to listOf(mapOf("type" to "text", "text" to text, "marks" to listOf(mapOf("type" to "strong")))),
        )

    private fun bulletListParagraphs(items: List<String>): Map<String, Any?> =
        mapOf(
            "type" to "bulletList",
            "content" to
                items.map { item ->
                    mapOf(
                        "type" to "listItem",
                        "content" to listOf(mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to item)))),
                    )
                },
        )

    private fun buildCell(
        text: String,
        header: Boolean,
    ): Map<String, Any?> =
        if (header) {
            mapOf(
                "type" to "tableHeader",
                "attrs" to mapOf("colspan" to 1, "rowspan" to 1),
                "content" to
                    listOf(
                        mapOf(
                            "type" to "paragraph",
                            "content" to
                                listOf(
                                    mapOf(
                                        "type" to "text",
                                        "text" to text,
                                        "marks" to listOf(mapOf("type" to "strong")),
                                    ),
                                ),
                        ),
                    ),
            )
        } else {
            mapOf(
                "type" to "tableCell",
                "attrs" to mapOf("colspan" to 1, "rowspan" to 1),
                "content" to
                    listOf(
                        mapOf(
                            "type" to "paragraph",
                            "content" to listOf(mapOf("type" to "text", "text" to text)),
                        ),
                    ),
            )
        }

    private fun statusPill(state: SprintReviewState): String =
        when (state) {
            SprintReviewState.BEARBEITET -> "✅"
            SprintReviewState.OFFENE_FRAGE -> "❓"
            SprintReviewState.REFINEMENT -> "📝"
            SprintReviewState.FAILED -> "⚠️"
        }

    private fun t(key: String): String = i18n.tFor(copilot.resolveLanguage(), "sprint_review.$key")

    private fun t(
        key: String,
        vararg args: Any,
    ): String = i18n.tFor(copilot.resolveLanguage(), "sprint_review.$key", *args)

    private fun translateReason(reasonKey: String): String {
        val key = "row_reason_$reasonKey"
        val translated = i18n.tFor(copilot.resolveLanguage(), "sprint_review.$key")
        return if (translated == "sprint_review.$key" || translated.isBlank()) reasonKey else translated
    }
}
