package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.jira.JiraSprintIssueKeyListerPort
import com.stacklens.application.port.out.knowledge.ConfluenceHierarchyPort
import com.stacklens.application.port.out.knowledge.JiraHierarchyPort
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeIngestionJobPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.port.out.knowledge.extraction.DocumentTextExtractor
import com.stacklens.application.service.knowledge.ingestor.KnowledgeSourceIngestor

data class KnowledgeSourceServiceDeps(
    val basePort: KnowledgeBasePort,
    val sourcePort: KnowledgeSourcePort,
    val documentPort: KnowledgeDocumentPort,
    val chunkPort: KnowledgeChunkPort,
    val jobPort: KnowledgeIngestionJobPort,
    val chunker: KnowledgeChunker,
    val dispatcher: KnowledgeEmbeddingDispatcher,
    val configPort: KnowledgeEmbeddingConfigPort,
    val extractors: List<DocumentTextExtractor>,
    val confluenceAdapter: ConfluenceHierarchyPort,
    val jiraAdapter: JiraHierarchyPort,
    val confluenceConfigPort: ConfluenceConfigPort,
    val jiraConfigPort: JiraConfigPort,
    val indexPort: KnowledgeIndexPort,
    val sprintReviewPort: com.stacklens.application.port.out.SprintReviewJiraPort,
    val sprintIssueKeyLister: JiraSprintIssueKeyListerPort,
    val ingestors: List<KnowledgeSourceIngestor>,
    val documentIngester: KnowledgeDocumentIngester,
)
