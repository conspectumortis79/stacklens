package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.port.out.knowledge.extraction.DocumentTextExtractor
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceStatus
import com.stacklens.domain.model.knowledge.KnowledgeSourceType

@ApplicationService
class LocalKnowledgeSourceCreator(
    private val basePort: KnowledgeBasePort,
    private val sourcePort: KnowledgeSourcePort,
    private val extractors: List<DocumentTextExtractor>,
    private val documentIngester: KnowledgeDocumentIngester,
    private val clock: ClockPort,
) {
    fun createManualSource(
        knowledgeBaseId: Long,
        name: String,
        content: String,
        externalReference: String?,
    ): KnowledgeSource {
        require(content.isNotBlank()) { "Manual knowledge content must not be blank" }
        if (basePort.findById(knowledgeBaseId) == null) {
            throw NoSuchElementException("Knowledge base $knowledgeBaseId not found")
        }
        val now = clock.localDateTime()
        val source =
            sourcePort.save(
                KnowledgeSource(
                    knowledgeBaseId = knowledgeBaseId,
                    name = name,
                    type = KnowledgeSourceType.MANUAL,
                    active = true,
                    externalReference = externalReference,
                    autoManaged = false,
                    status = KnowledgeSourceStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        val docStatus =
            documentIngester.ingestDocument(
                source = source,
                title = name,
                plainText = content,
                sourceUri = externalReference,
                sourceType = KnowledgeSourceType.MANUAL,
                mimeType = "text/plain",
                metadata = mapOf("origin" to "manual"),
            )
        source.lastSyncAt = now
        documentIngester.applyOutcome(source, now, docStatus == KnowledgeDocumentStatus.FAILED)
        return sourcePort.save(source)
    }

    fun createFileSource(
        knowledgeBaseId: Long,
        originalFilename: String,
        contentBytes: ByteArray,
    ): KnowledgeSource {
        val now = clock.localDateTime()
        val source =
            sourcePort.save(
                KnowledgeSource(
                    knowledgeBaseId = knowledgeBaseId,
                    name = originalFilename,
                    type = KnowledgeSourceType.FILE,
                    active = true,
                    autoManaged = false,
                    status = KnowledgeSourceStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        val text = extractText(originalFilename, contentBytes)
        val docStatus =
            documentIngester.ingestDocument(
                source = source,
                title = originalFilename,
                plainText = text,
                sourceUri = null,
                sourceType = KnowledgeSourceType.FILE,
                mimeType = sniffMimeType(originalFilename),
                metadata = mapOf("filename" to originalFilename, "size" to contentBytes.size.toString()),
            )
        source.lastSyncAt = now
        documentIngester.applyOutcome(source, now, docStatus == KnowledgeDocumentStatus.FAILED)
        return sourcePort.save(source)
    }

    private fun extractText(
        filename: String,
        content: ByteArray,
    ): String {
        val extractor =
            extractors.firstOrNull { it.supports(filename, content) }
                ?: throw IllegalArgumentException(
                    "No knowledge text extractor available for file '$filename'. Supported: PDF, DOC, DOCX, TXT, MD.",
                )
        return extractor.extract(filename, content)
    }

    private fun sniffMimeType(filename: String): String? {
        val lower = filename.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lower.endsWith(".doc") -> "application/msword"
            lower.endsWith(".md") || lower.endsWith(".markdown") -> "text/markdown"
            lower.endsWith(".txt") -> "text/plain"
            else -> null
        }
    }
}
