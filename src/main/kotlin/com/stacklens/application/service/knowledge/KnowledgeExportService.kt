package com.stacklens.application.service.knowledge

import com.stacklens.application.collaboration.KnowledgeExporter
import com.stacklens.application.port.inbound.knowledge.KnowledgeExportUseCase
import com.stacklens.application.port.model.KnowledgeImportPreview
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveReaderPort
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveWriterPort
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeBaseSnapshot
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions

@ApplicationService
class KnowledgeExportService(
    private val basePort: KnowledgeBasePort,
    private val sourcePort: KnowledgeSourcePort,
    private val documentPort: KnowledgeDocumentPort,
    private val chunkPort: KnowledgeChunkPort,
    private val writerPort: KnowledgeArchiveWriterPort,
    private val readerPort: KnowledgeArchiveReaderPort,
) : KnowledgeExportUseCase,
    KnowledgeExporter {
    override fun exportBase(
        knowledgeBaseId: Long,
        options: KnowledgeExportOptions,
        password: String,
    ): ByteArray {
        require(password.isNotBlank()) { "Export password must not be blank" }
        val base =
            basePort.findById(knowledgeBaseId)
                ?: error("Knowledge base $knowledgeBaseId not found")
        val sources = sourcePort.findByKnowledgeBaseId(base.id).map { it.toArchiveRecord() }
        val documents =
            sources.flatMap { sourceRec ->
                val resolved = sourcePort.findByStableId(sourceRec.stableId) ?: return@flatMap emptyList()
                val sourceDbId = resolved.id
                documentPort.findBySourceIdOrderByIdAsc(sourceDbId).map { dbDoc ->
                    KnowledgeArchiveRecord.Document(
                        stableId = dbDoc.stableId,
                        sourceStableId = sourceRec.stableId,
                        externalId = dbDoc.externalId,
                        title = dbDoc.title,
                        sourceUri = dbDoc.sourceUri,
                        mimeType = dbDoc.mimeType,
                        plainText = dbDoc.plainText,
                        contentHash = dbDoc.contentHash,
                        metadata = parseMetadata(dbDoc.metadataJson),
                    )
                }
            }
        val chunks =
            documents.flatMap { docRec ->
                val dbDoc = documentPort.findByStableId(docRec.stableId) ?: return@flatMap emptyList()
                chunkPort.findByDocumentIdOrderByOrdinalAsc(dbDoc.id).map { chunk ->
                    KnowledgeArchiveRecord.Chunk(
                        stableId = chunk.stableId,
                        documentStableId = docRec.stableId,
                        ordinal = chunk.ordinal,
                        sectionTitle = chunk.sectionTitle,
                        content = chunk.content,
                        contentHash = chunk.contentHash,
                        tokenEstimate = chunk.tokenEstimate,
                        vectorBytes = if (options.includeVectors) chunk.embeddingBlob else null,
                        vectorDimension = chunk.embeddingDimension,
                    )
                }
            }
        val baseSnapshot =
            KnowledgeBaseSnapshot(
                knowledgeBaseStableId = base.stableId,
                knowledgeBaseName = base.name,
                knowledgeBaseDescription = base.description,
                sources = sources,
                documents = documents,
                chunks = chunks,
                embeddingProvider = null,
                embeddingModel = null,
                embeddingDimension = null,
                embeddingProfile = null,
            )
        return writerPort.write(baseSnapshot, options, password)
    }

    override fun inspect(
        archiveBytes: ByteArray,
        password: String?,
    ): KnowledgeImportPreview {
        val manifest = readerPort.inspect(archiveBytes, password)
        return KnowledgeImportPreview(
            knowledgeBaseName = manifest.knowledgeBaseName,
            sourceCount = manifest.sourceCount,
            documentCount = manifest.documentCount,
            chunkCount = manifest.chunkCount,
            containsVectors = manifest.containsVectors,
            embeddingProvider =
                manifest.embeddingProvider?.let {
                    runCatching { KnowledgeEmbeddingProvider.valueOf(it) }.getOrNull()
                },
            embeddingModel = manifest.embeddingModel,
            embeddingDimension = manifest.embeddingDimension,
        )
    }
}

private fun com.stacklens.domain.model.knowledge.KnowledgeSource.toArchiveRecord() =
    KnowledgeArchiveRecord.Source(
        stableId = stableId,
        name = name,
        type = type.name,
        rootUrl = rootUrl,
        externalConfigName = null,
        externalReference = externalReference,
        includeChildren = includeChildren,
        includeComments = includeComments,
        maxDepth = maxDepth,
        active = active,
    )

private fun parseMetadata(json: String?): Map<String, String> {
    if (json.isNullOrBlank()) return emptyMap()
    return json
        .split(",")
        .mapNotNull { entry ->
            val idx = entry.indexOf('=')
            if (idx > 0) entry.substring(0, idx) to entry.substring(idx + 1) else null
        }.toMap()
}
