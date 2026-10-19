package com.stacklens.application.port.inbound.knowledge

import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportResult
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions

interface KnowledgeBundleExportUseCase {
    fun exportAll(
        options: KnowledgeExportOptions,
        password: String,
    ): ByteArray
}

interface KnowledgeBundleImportUseCase {
    fun inspect(bundleBytes: ByteArray): KnowledgeBundlePreview

    fun import(
        bundleBytes: ByteArray,
        password: String,
        mode: KnowledgeImportMode,
    ): List<KnowledgeImportResult>
}

data class KnowledgeBundlePreview(
    val entryCount: Int,
    val bases: List<KnowledgeBundlePreviewEntry>,
)

data class KnowledgeBundlePreviewEntry(
    val entryName: String,
    val knowledgeBaseStableId: String,
    val knowledgeBaseName: String,
    val knowledgeBaseDescription: String,
    val sourceCount: Int,
    val documentCount: Int,
    val chunkCount: Int,
    val containsVectors: Boolean,
)
