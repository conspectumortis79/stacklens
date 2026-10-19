package com.stacklens.application.service.knowledge

import com.stacklens.application.collaboration.KnowledgeBaseReader
import com.stacklens.application.collaboration.KnowledgeExporter
import com.stacklens.application.collaboration.KnowledgeImporter
import com.stacklens.application.port.inbound.knowledge.KnowledgeBundleExportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeBundleImportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeBundlePreview
import com.stacklens.application.port.inbound.knowledge.KnowledgeBundlePreviewEntry
import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportRequest
import com.stacklens.application.port.model.KnowledgeImportResult
import com.stacklens.application.port.out.knowledge.KnowledgeBundleArchive
import com.stacklens.application.port.out.knowledge.KnowledgeBundlePort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions
import org.slf4j.LoggerFactory

@ApplicationService
class KnowledgeBundleService(
    private val bundlePort: KnowledgeBundlePort,
    private val baseUseCase: KnowledgeBaseReader,
    private val singleBaseExport: KnowledgeExporter,
    private val singleBaseImport: KnowledgeImporter,
) : KnowledgeBundleExportUseCase,
    KnowledgeBundleImportUseCase {
    private val log = LoggerFactory.getLogger(KnowledgeBundleService::class.java)

    override fun exportAll(
        options: KnowledgeExportOptions,
        password: String,
    ): ByteArray {
        require(password.isNotBlank()) { "Export password must not be blank" }
        val bases = baseUseCase.listBases()
        if (bases.isEmpty()) {
            error("Keine Wissensdatenbanken zum Exportieren vorhanden")
        }
        val archives = ArrayList<KnowledgeBundleArchive>(bases.size)
        val seenNames = HashSet<String>()
        for (base in bases) {
            val bytes =
                singleBaseExport.exportBase(
                    knowledgeBaseId = base.id,
                    options = options,
                    password = password,
                )
            val preview = singleBaseExport.inspect(bytes, password)
            val displayName = preview.knowledgeBaseName.ifBlank { base.name }
            val safeName = displayName.safeFileName()
            val entryName =
                "bases/" + ensureUnique(safeName, seenNames) + ".slknowledge"
            archives.add(
                KnowledgeBundleArchive(
                    entryName = entryName,
                    displayName = displayName,
                    knowledgeBaseStableId = base.stableId,
                    knowledgeBaseName = displayName,
                    knowledgeBaseDescription = base.description,
                    sourceCount = preview.sourceCount,
                    documentCount = preview.documentCount,
                    chunkCount = preview.chunkCount,
                    containsVectors = preview.containsVectors,
                    bytes = bytes,
                ),
            )
        }
        val bytes = bundlePort.write(archives)
        log.info(
            "Exported knowledge bundle ({} bases, {} bytes total)",
            archives.size,
            bytes.size,
        )
        return bytes
    }

    override fun inspect(bundleBytes: ByteArray): KnowledgeBundlePreview {
        val entries = bundlePort.inspect(bundleBytes)
        return KnowledgeBundlePreview(
            entryCount = entries.size,
            bases =
                entries.map { e ->
                    KnowledgeBundlePreviewEntry(
                        entryName = e.entryName,
                        knowledgeBaseStableId = e.knowledgeBaseStableId,
                        knowledgeBaseName = e.knowledgeBaseName,
                        knowledgeBaseDescription = e.knowledgeBaseDescription,
                        sourceCount = e.sourceCount,
                        documentCount = e.documentCount,
                        chunkCount = e.chunkCount,
                        containsVectors = e.containsVectors,
                    )
                },
        )
    }

    override fun import(
        bundleBytes: ByteArray,
        password: String,
        mode: KnowledgeImportMode,
    ): List<KnowledgeImportResult> {
        require(password.isNotBlank()) { "Password is required to decrypt the bundle" }
        val entries = bundlePort.inspect(bundleBytes)
        val results = ArrayList<KnowledgeImportResult>(entries.size)
        for (entry in entries) {
            val archiveBytes = bundlePort.readEntry(bundleBytes, entry.entryName)
            val result =
                singleBaseImport.import(
                    KnowledgeImportRequest(
                        archiveBytes = archiveBytes,
                        password = password,
                        mode = mode,
                        targetKnowledgeBaseName = entry.knowledgeBaseName,
                    ),
                )
            results.add(result)
            log.info(
                "Imported bundle entry '{}' into base {} ({} docs, {} chunks)",
                entry.entryName,
                result.knowledgeBaseId,
                result.documentsImported,
                result.chunksImported,
            )
        }
        return results
    }

    private fun String.safeFileName(): String =
        this
            .lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
            .ifBlank { "base" }

    private fun ensureUnique(
        base: String,
        seen: MutableSet<String>,
    ): String {
        if (seen.add(base)) return base
        var i = 2
        while (!seen.add("$base-$i")) i++
        return "$base-$i"
    }
}
