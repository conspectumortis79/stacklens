package com.stacklens.application.collaboration

import com.stacklens.application.port.model.KnowledgeImportPreview
import com.stacklens.application.port.model.KnowledgeImportRequest
import com.stacklens.application.port.model.KnowledgeImportResult
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions

interface KnowledgeBaseReader {
    fun listBases(): List<KnowledgeBase>

    fun ensureAnalysisBase()
}

interface KnowledgeExporter {
    fun exportBase(
        knowledgeBaseId: Long,
        options: KnowledgeExportOptions,
        password: String,
    ): ByteArray

    fun inspect(
        archiveBytes: ByteArray,
        password: String?,
    ): KnowledgeImportPreview
}

interface KnowledgeImporter {
    fun import(request: KnowledgeImportRequest): KnowledgeImportResult
}
