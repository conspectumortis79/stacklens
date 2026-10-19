package com.stacklens.infrastructure.config.deps

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.SprintReviewPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class KnowledgeSourceServiceDepsConfig {
    @Bean
    fun knowledgeSourceServiceDeps(
        basePort: com.stacklens.application.port.out.knowledge.KnowledgeBasePort,
        sourcePort: com.stacklens.application.port.out.knowledge.KnowledgeSourcePort,
        documentPort: com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort,
        chunkPort: com.stacklens.application.port.out.knowledge.KnowledgeChunkPort,
        jobPort: com.stacklens.application.port.out.knowledge.KnowledgeIngestionJobPort,
        chunker: com.stacklens.application.service.knowledge.KnowledgeChunker,
        dispatcher: com.stacklens.application.service.knowledge.KnowledgeEmbeddingDispatcher,
        configPort: com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort,
        extractors: List<com.stacklens.application.port.out.knowledge.extraction.DocumentTextExtractor>,
        confluenceAdapter: com.stacklens.application.port.out.knowledge.ConfluenceHierarchyPort,
        jiraAdapter: com.stacklens.application.port.out.knowledge.JiraHierarchyPort,
        confluenceConfigPort: ConfluenceConfigPort,
        jiraConfigPort: JiraConfigPort,
        indexPort: com.stacklens.application.port.out.knowledge.KnowledgeIndexPort,
        sprintReviewPort: com.stacklens.application.port.out.SprintReviewJiraPort,
        sprintIssueKeyLister: com.stacklens.application.port.out.jira.JiraSprintIssueKeyListerPort,
        ingestors: List<com.stacklens.application.service.knowledge.ingestor.KnowledgeSourceIngestor>,
        documentIngester: com.stacklens.application.service.knowledge.KnowledgeDocumentIngester,
    ): com.stacklens.application.service.knowledge.KnowledgeSourceServiceDeps =
        com.stacklens.application.service.knowledge.KnowledgeSourceServiceDeps(
            basePort = basePort,
            sourcePort = sourcePort,
            documentPort = documentPort,
            chunkPort = chunkPort,
            jobPort = jobPort,
            chunker = chunker,
            dispatcher = dispatcher,
            configPort = configPort,
            extractors = extractors,
            confluenceAdapter = confluenceAdapter,
            jiraAdapter = jiraAdapter,
            confluenceConfigPort = confluenceConfigPort,
            jiraConfigPort = jiraConfigPort,
            indexPort = indexPort,
            sprintReviewPort = sprintReviewPort,
            sprintIssueKeyLister = sprintIssueKeyLister,
            ingestors = ingestors,
            documentIngester = documentIngester,
        )
}

@Configuration
class SprintReviewServiceDepsConfig {
    @Bean
    fun sprintReviewServiceDeps(
        sprintReviewRepo: SprintReviewPort,
        jiraConfigRepo: JiraConfigPort,
        jira: com.stacklens.application.port.out.SprintReviewJiraPort,
        copilot: com.stacklens.application.port.out.ai.CopilotPort,
        aiUsageContext: AiUsageContextPort,
        promptLoader: PromptReaderPort,
        jsonCodec: JsonCodecPort,
        i18n: I18nPort,
        asyncExecutor: AsyncExecutorPort,
        startupHealer: com.stacklens.application.collaboration.StartupRecovery,
    ): com.stacklens.application.service.SprintReviewServiceDeps =
        com.stacklens.application.service.SprintReviewServiceDeps(
            sprintReviewRepo = sprintReviewRepo,
            jiraConfigRepo = jiraConfigRepo,
            jira = jira,
            copilot = copilot,
            aiUsageContext = aiUsageContext,
            promptLoader = promptLoader,
            jsonCodec = jsonCodec,
            i18n = i18n,
            asyncExecutor = asyncExecutor,
            startupHealer = startupHealer,
        )
}

@Configuration
class KnowledgePresentationConfig {
    @Bean
    fun germanKnowledgePresentation(): com.stacklens.adapter.out.presentation.GermanKnowledgePresentation = com.stacklens.adapter.out.presentation.GermanKnowledgePresentation
}
