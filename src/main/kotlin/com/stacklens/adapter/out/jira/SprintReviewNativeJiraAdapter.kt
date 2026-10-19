package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.ResolvedSprint
import com.stacklens.application.port.out.SprintReviewAdfDocument
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.application.port.out.SprintReviewTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SprintReviewRow
import org.springframework.stereotype.Component

@Component("sprintReviewNativeJiraPort")
class SprintReviewNativeJiraAdapter(
    private val sprintResolver: SprintResolver,
    private val sprintIssueLoader: SprintIssueLoader,
    private val adfDescriptionUpdater: AdfDescriptionUpdater,
    private val tableBuilder: SprintReviewTableBuilder,
    private val objectMapper: ObjectMapper,
) : SprintReviewJiraPort {
    override fun resolveSprintByName(
        config: JiraConfig?,
        sprintName: String,
    ): ResolvedSprint {
        requireNotNull(config) { "JiraConfig is required for native source mode" }
        return sprintResolver.resolve(config, sprintName)
    }

    override fun fetchSprintTickets(
        config: JiraConfig?,
        sprintId: Long,
    ): List<SprintReviewTicket> {
        requireNotNull(config) { "JiraConfig is required for native source mode" }
        return sprintIssueLoader.fetch(config, sprintId)
    }

    override fun renderAdfDocument(
        rows: List<SprintReviewRow>,
        includeImprovement: Boolean,
        sprintName: String,
    ): SprintReviewAdfDocument =
        SprintReviewAdfDocument(
            objectMapper.writeValueAsString(
                tableBuilder.buildAdfDocument(rows, includeImprovement, sprintName),
            ),
        )

    override fun appendAdfToDescription(
        config: JiraConfig?,
        targetKey: String,
        adfDocument: SprintReviewAdfDocument,
    ): String {
        requireNotNull(config) { "JiraConfig is required for native source mode" }
        val adfDoc: Map<String, Any?> = objectMapper.readValue(adfDocument.json, object : TypeReference<Map<String, Any?>>() {})
        return adfDescriptionUpdater.append(config, targetKey, adfDoc)
    }
}
