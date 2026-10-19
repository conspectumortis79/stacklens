package com.stacklens.application.service.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeSourceUseCase
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.knowledge.ingestor.KnowledgeSourceIngestor
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ApplicationTransactional
import com.stacklens.application.stereotype.ManuallyWired
import com.stacklens.domain.model.jira.JiraTicketKey
import com.stacklens.domain.model.knowledge.KnowledgeIngestionJob
import com.stacklens.domain.model.knowledge.KnowledgeJobStatus
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceStatus
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.slf4j.LoggerFactory

@ApplicationService
@ManuallyWired
class KnowledgeSourceService
    constructor(
        private val deps: KnowledgeSourceServiceDeps,
        private val lister: KnowledgeSourceLister,
        private val localCreator: LocalKnowledgeSourceCreator,
        private val clock: ClockPort,
    ) : KnowledgeSourceUseCase {
        private val sourcePort get() = deps.sourcePort
        private val documentPort get() = deps.documentPort
        private val chunkPort get() = deps.chunkPort
        private val jobPort get() = deps.jobPort
        private val indexPort get() = deps.indexPort
        private val ingestors get() = deps.ingestors
        private val documentIngester get() = deps.documentIngester

        private val log = LoggerFactory.getLogger(KnowledgeSourceService::class.java)

        private val ingestorsByType: Map<KnowledgeSourceType, KnowledgeSourceIngestor> by lazy {
            KnowledgeSourceType
                .entries
                .mapNotNull { type -> ingestors.firstOrNull { it.supports(type) }?.let { type to it } }
                .toMap()
        }

        private fun ingestorFor(type: KnowledgeSourceType): KnowledgeSourceIngestor =
            ingestorsByType[type]
                ?: error("No KnowledgeSourceIngestor is wired for source type $type; wired types: ${ingestorsByType.keys}")

        private fun ingestWithStrategy(
            type: KnowledgeSourceType,
            source: KnowledgeSource,
        ): KnowledgeSource {
            val ingestor = ingestorFor(type)
            val outcome = ingestor.ingest(source)
            require(outcome.ingestedCount > 0) {
                "${type.name} ingestor returned zero ingested documents for source ${source.id}"
            }
            val now = clock.localDateTime()
            source.lastSyncAt = now
            source.updatedAt = now
            source.errorMessage = null
            documentIngester.applyOutcome(source, now, outcome.anyDocFailed)
            return sourcePort.save(source)
        }

        private fun saveAndIngest(draft: KnowledgeSource): KnowledgeSource {
            val source = sourcePort.save(draft)
            return ingestWithStrategy(draft.type, source)
        }

        private fun registerWithFailureRecovery(draft: KnowledgeSource): KnowledgeSource =
            try {
                saveAndIngest(draft)
            } catch (e: Exception) {
                log.warn("Failed to ingest {} source: {}", draft.type.name, e.message)
                draft.errorMessage = e.message
                draft.status = KnowledgeSourceStatus.FAILED
                sourcePort.save(draft)
            }

        override fun listSources(knowledgeBaseId: Long?): List<KnowledgeSource> = lister.listSources(knowledgeBaseId)

        override fun listSourcesByType(type: KnowledgeSourceType): List<KnowledgeSource> = lister.listSourcesByType(type)

        override fun getSource(id: Long): KnowledgeSource? = lister.getSource(id)

        @ApplicationTransactional
        override fun createManualSource(
            knowledgeBaseId: Long,
            name: String,
            content: String,
            externalReference: String?,
        ): KnowledgeSource = localCreator.createManualSource(knowledgeBaseId, name, content, externalReference)

        @ApplicationTransactional
        override fun createFileSource(
            knowledgeBaseId: Long,
            originalFilename: String,
            contentBytes: ByteArray,
        ): KnowledgeSource = localCreator.createFileSource(knowledgeBaseId, originalFilename, contentBytes)

        @ApplicationTransactional
        override fun createConfluenceSource(
            knowledgeBaseId: Long,
            confluenceConfigId: Long,
            rootPageUrl: String,
            includeChildren: Boolean,
            maxDepth: Int,
        ): KnowledgeSource {
            val now = clock.localDateTime()
            return registerWithFailureRecovery(
                KnowledgeSource(
                    knowledgeBaseId = knowledgeBaseId,
                    name = rootPageUrl,
                    type = KnowledgeSourceType.CONFLUENCE,
                    active = true,
                    rootUrl = rootPageUrl,
                    externalConfigId = confluenceConfigId,
                    externalReference = rootPageUrl,
                    includeChildren = includeChildren,
                    maxDepth = maxDepth,
                    autoManaged = false,
                    status = KnowledgeSourceStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        @ApplicationTransactional
        override fun createJiraSource(
            knowledgeBaseId: Long,
            jiraConfigId: Long,
            rootTicketKey: JiraTicketKey,
            includeChildren: Boolean,
            includeComments: Boolean,
            maxDepth: Int,
            includeLinkedTickets: Boolean,
            includeParent: Boolean,
        ): KnowledgeSource {
            val keyString = rootTicketKey.value
            val now = clock.localDateTime()
            return registerWithFailureRecovery(
                KnowledgeSource(
                    knowledgeBaseId = knowledgeBaseId,
                    name = keyString,
                    type = KnowledgeSourceType.JIRA,
                    active = true,
                    externalConfigId = jiraConfigId,
                    externalReference = keyString,
                    includeChildren = includeChildren,
                    includeComments = includeComments,
                    maxDepth = maxDepth,
                    includeLinkedTickets = includeLinkedTickets,
                    includeParent = includeParent,
                    autoManaged = false,
                    status = KnowledgeSourceStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        @ApplicationTransactional
        override fun createJiraSprintSource(
            knowledgeBaseId: Long,
            jiraConfigId: Long,
            sprintReference: String,
            includeChildren: Boolean,
            includeComments: Boolean,
            maxDepth: Int,
            includeLinkedTickets: Boolean,
            includeParent: Boolean,
        ): KnowledgeSource {
            val trimmed = sprintReference.trim()
            require(trimmed.isNotEmpty()) {
                "Bitte einen Sprint-Namen oder eine Sprint-ID angeben (z.B. 'Sprint 42' oder '1234')."
            }
            val now = clock.localDateTime()
            return saveAndIngest(
                KnowledgeSource(
                    knowledgeBaseId = knowledgeBaseId,
                    name = sprintReference,
                    type = KnowledgeSourceType.JIRA_SPRINT,
                    active = true,
                    rootUrl = null,
                    externalConfigId = jiraConfigId,
                    externalReference = sprintReference,
                    includeChildren = includeChildren,
                    includeComments = includeComments,
                    maxDepth = maxDepth,
                    includeLinkedTickets = includeLinkedTickets,
                    includeParent = includeParent,
                    autoManaged = false,
                    status = KnowledgeSourceStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        @ApplicationTransactional
        override fun syncSource(id: Long): KnowledgeIngestionJob {
            val source = sourcePort.findById(id) ?: error("Source $id not found")
            val job =
                KnowledgeIngestionJob(
                    sourceId = id,
                    status = KnowledgeJobStatus.QUEUED,
                    phase = "queued",
                )
            jobPort.save(job)
            return runCatching {
                val documents = documentPort.findBySourceIdOrderByIdAsc(id)
                for (doc in documents) {
                    chunkPort.deleteByDocumentId(doc.id)
                    indexPort.removeDocument(doc.knowledgeBaseId, doc.id)
                }
                documentPort.deleteBySourceId(id)
                val updated = ingestWithStrategy(source.type, source)
                val done = jobPort.findById(job.id) ?: job
                done.status = KnowledgeJobStatus.COMPLETED
                done.finishedAt = clock.localDateTime()
                jobPort.save(done)
                if (updated.status == KnowledgeSourceStatus.FAILED) {
                    throw IllegalStateException(updated.errorMessage ?: "Ingestion reported failure")
                }
                done
            }.getOrElse { e ->
                val failed = jobPort.findById(job.id) ?: job
                failed.status = KnowledgeJobStatus.FAILED
                failed.errorMessage = e.message
                failed.finishedAt = clock.localDateTime()
                jobPort.save(failed)
                source.status = KnowledgeSourceStatus.FAILED
                source.errorMessage = e.message
                source.updatedAt = clock.localDateTime()
                sourcePort.save(source)
                failed
            }
        }

        override fun reindexSource(id: Long): KnowledgeIngestionJob = syncSource(id)

        fun updateSource(
            id: Long,
            name: String?,
            active: Boolean?,
        ): KnowledgeSource {
            val source = sourcePort.findById(id) ?: error("Source $id not found")
            if (name != null) source.name = name
            if (active != null) source.active = active
            source.updatedAt = clock.localDateTime()
            return sourcePort.save(source)
        }

        @ApplicationTransactional
        override fun deleteSource(id: Long) {
            val documents = documentPort.findBySourceIdOrderByIdAsc(id)
            for (doc in documents) {
                chunkPort.deleteByDocumentId(doc.id)
                indexPort.removeDocument(doc.knowledgeBaseId, doc.id)
            }
            documentPort.deleteBySourceId(id)
            sourcePort.deleteById(id)
        }
    }
