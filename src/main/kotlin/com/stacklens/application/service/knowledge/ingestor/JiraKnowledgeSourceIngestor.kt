package com.stacklens.application.service.knowledge.ingestor

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.knowledge.JiraHierarchyPort
import com.stacklens.application.port.out.knowledge.JiraIssueSnapshot
import com.stacklens.application.service.knowledge.KnowledgeDocumentIngester
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType

@ApplicationService
class JiraKnowledgeSourceIngestor(
    private val jiraAdapter: JiraHierarchyPort,
    private val jiraConfigPort: JiraConfigPort,
    private val documentIngester: KnowledgeDocumentIngester,
) : KnowledgeSourceIngestor {
    override fun supports(type: KnowledgeSourceType): Boolean = type == KnowledgeSourceType.JIRA

    override fun ingest(source: KnowledgeSource): KnowledgeSourceIngestionOutcome {
        val jiraConfigId =
            source.externalConfigId
                ?: error("Jira source ${source.id} is missing a config id")
        val config =
            jiraConfigPort.findJiraConfigById(jiraConfigId)
                ?: error("Jira config $jiraConfigId not found")
        val rootKey =
            source.externalReference
                ?: error("Jira source ${source.id} is missing a root ticket reference")
        jiraAdapter.ensureHostMatches(config, rootKey)
        var count = 0
        var anyDocFailed = false
        for (
        snapshot: JiraIssueSnapshot in
        jiraAdapter.walkHierarchy(
            config = config,
            rootTicketKeyOrUrl = rootKey,
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
                    sourceType = KnowledgeSourceType.JIRA,
                    mimeType = "application/json",
                    metadata = mapOf("ticketKey" to snapshot.key, "depth" to snapshot.depth.toString()),
                )
            if (docStatus == KnowledgeDocumentStatus.FAILED) anyDocFailed = true
            count++
        }
        if (count == 0) error("Jira hierarchy walk returned no issues for $rootKey")
        return KnowledgeSourceIngestionOutcome(ingestedCount = count, anyDocFailed = anyDocFailed)
    }
}
