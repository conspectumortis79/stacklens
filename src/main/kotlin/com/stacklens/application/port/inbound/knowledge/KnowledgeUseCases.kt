package com.stacklens.application.port.inbound.knowledge

import com.stacklens.domain.model.jira.JiraTicketKey
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import com.stacklens.domain.model.knowledge.KnowledgeIngestionJob
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.domain.model.knowledge.RetrievedKnowledge

interface KnowledgeConfigUseCase {
    fun getConfig(): KnowledgeEmbeddingConfig

    fun saveConfig(config: KnowledgeEmbeddingConfig): KnowledgeEmbeddingConfig

    fun testConnection(): KnowledgeConnectionTestResult

    fun availableModels(
        provider: KnowledgeEmbeddingProvider? = null,
        ollamaUrl: String? = null,
        openRouterApiKey: String? = null,
    ): List<String>
}

data class KnowledgeConnectionTestResult(
    val ok: Boolean,
    val provider: KnowledgeEmbeddingProvider,
    val model: String,
    val dimension: Int?,
    val errorMessage: String? = null,
)

interface KnowledgeBaseUseCase {
    fun createBase(
        name: String,
        description: String,
        includeInGlobalChat: Boolean = true,
    ): KnowledgeBase

    fun updateBase(
        id: Long,
        name: String?,
        description: String?,
        active: Boolean?,
        includeInGlobalChat: Boolean?,
    ): KnowledgeBase

    fun clearBase(id: Long)

    fun deleteBase(id: Long)

    fun listBases(): List<KnowledgeBase>

    fun ensureAnalysisBase()
}

interface KnowledgeSourceUseCase {
    fun listSources(knowledgeBaseId: Long? = null): List<KnowledgeSource>

    fun listSourcesByType(type: KnowledgeSourceType): List<KnowledgeSource>

    fun getSource(id: Long): KnowledgeSource?

    fun createManualSource(
        knowledgeBaseId: Long,
        name: String,
        content: String,
        externalReference: String? = null,
    ): KnowledgeSource

    fun createFileSource(
        knowledgeBaseId: Long,
        originalFilename: String,
        contentBytes: ByteArray,
    ): KnowledgeSource

    fun createConfluenceSource(
        knowledgeBaseId: Long,
        confluenceConfigId: Long,
        rootPageUrl: String,
        includeChildren: Boolean,
        maxDepth: Int,
    ): KnowledgeSource

    fun createJiraSource(
        knowledgeBaseId: Long,
        jiraConfigId: Long,
        rootTicketKey: JiraTicketKey,
        includeChildren: Boolean,
        includeComments: Boolean,
        maxDepth: Int,
        includeLinkedTickets: Boolean,
        includeParent: Boolean,
    ): KnowledgeSource

    fun createJiraSprintSource(
        knowledgeBaseId: Long,
        jiraConfigId: Long,
        sprintReference: String,
        includeChildren: Boolean,
        includeComments: Boolean,
        maxDepth: Int,
        includeLinkedTickets: Boolean,
        includeParent: Boolean,
    ): KnowledgeSource

    fun syncSource(id: Long): KnowledgeIngestionJob

    fun reindexSource(id: Long): KnowledgeIngestionJob

    fun deleteSource(id: Long)
}

interface KnowledgeJobUseCase {
    fun listJobs(limit: Int = 100): List<KnowledgeIngestionJob>

    fun getJob(id: Long): KnowledgeIngestionJob?

    fun cancelJob(id: Long)
}

interface KnowledgeRetrievalUseCase {
    fun retrieveForChat(
        query: String,
        limit: Int? = null,
    ): List<RetrievedKnowledge>
}
