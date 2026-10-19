package com.stacklens.application.port.inbound.knowledge

import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportPreview
import com.stacklens.application.port.model.KnowledgeImportRequest
import com.stacklens.application.port.model.KnowledgeImportResult
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions

interface KnowledgeExportUseCase {
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

interface KnowledgeImportUseCase {
    fun import(request: KnowledgeImportRequest): KnowledgeImportResult
}

interface KnowledgeLocalFileImportUseCase {
    fun importFromLocalFile(
        path: String,
        password: String?,
        mode: KnowledgeImportMode,
    ): List<KnowledgeImportResult>

    fun importAggregatedFromLocalFile(
        path: String,
        password: String?,
        mode: KnowledgeImportMode,
    ): List<KnowledgeImportResult>
}
