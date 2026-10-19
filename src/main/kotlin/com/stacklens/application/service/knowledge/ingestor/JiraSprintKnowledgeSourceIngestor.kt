package com.stacklens.application.service.knowledge.ingestor

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.application.port.out.jira.JiraSprintIssueKeyListerPort
import com.stacklens.application.port.out.knowledge.JiraHierarchyPort
import com.stacklens.application.port.out.knowledge.JiraIssueSnapshot
import com.stacklens.application.service.knowledge.KnowledgeDocumentIngester
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType

@ApplicationService
class JiraSprintKnowledgeSourceIngestor(
    private val jiraAdapter: JiraHierarchyPort,
    private val jiraConfigPort: JiraConfigPort,
    private val sprintReviewPort: SprintReviewJiraPort,
    private val sprintIssueKeyLister: JiraSprintIssueKeyListerPort,
    private val documentIngester: KnowledgeDocumentIngester,
) : KnowledgeSourceIngestor {
    override fun supports(type: KnowledgeSourceType): Boolean = type == KnowledgeSourceType.JIRA_SPRINT

    override fun ingest(source: KnowledgeSource): KnowledgeSourceIngestionOutcome {
        val jiraConfigId =
            source.externalConfigId
                ?: error("Jira sprint source ${source.id} is missing a config id")
        val config =
            jiraConfigPort.findJiraConfigById(jiraConfigId)
                ?: error("Jira config $jiraConfigId not found")
        val sprintReference =
            source.externalReference
                ?: error("Jira sprint source ${source.id} is missing a sprint reference")
        val sprintId = resolveSprintId(config, sprintReference)
        val issueKeys = sprintIssueKeyLister.listTopLevelIssueKeys(config, sprintId)
        if (issueKeys.isEmpty()) error("Jira sprint $sprintId ($sprintReference) enthält keine Top-Level-Tickets.")
        var count = 0
        var anyDocFailed = false
        for (key in issueKeys) {
            for (
            snapshot: JiraIssueSnapshot in
            jiraAdapter.walkHierarchy(
                config = config,
                rootTicketKeyOrUrl = key,
                includeChildren = source.includeChildren,
                includeComments = source.includeComments,
                maxDepth = source.maxDepth,
                maxIssues = 1000,
                includeLinkedTickets = source.includeLinkedTickets,
                includeParent = source.includeParent,
            )
            ) {
                val docStatus =
                    documentIngester.ingestDocument(
                        source = source,
                        title = snapshot.title,
                        plainText = snapshot.plainText,
                        sourceUri = snapshot.sourceUri,
                        sourceType = KnowledgeSourceType.JIRA_SPRINT,
                        mimeType = "application/json",
                        metadata =
                            mapOf(
                                "ticketKey" to snapshot.key,
                                "sprintId" to sprintId.toString(),
                                "sprintReference" to sprintReference,
                                "depth" to snapshot.depth.toString(),
                            ),
                    )
                if (docStatus == KnowledgeDocumentStatus.FAILED) anyDocFailed = true
                count++
            }
        }
        if (count == 0) error("Jira sprint $sprintId ($sprintReference) lieferte keine importierbaren Tickets.")
        return KnowledgeSourceIngestionOutcome(ingestedCount = count, anyDocFailed = anyDocFailed)
    }

    private fun resolveSprintId(
        config: JiraConfig,
        sprintReference: String,
    ): Long {
        val numeric = sprintReference.trim().toLongOrNull()
        if (numeric != null) return numeric
        return sprintReviewPort.resolveSprintByName(config, sprintReference.trim()).id
    }
}
