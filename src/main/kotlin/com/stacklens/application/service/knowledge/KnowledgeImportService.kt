package com.stacklens.application.service.knowledge

import com.stacklens.application.collaboration.KnowledgeImporter
import com.stacklens.application.port.inbound.knowledge.KnowledgeImportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeLocalFileImportUseCase
import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportRequest
import com.stacklens.application.port.model.KnowledgeImportResult
import com.stacklens.application.port.out.file.FileReadWritePort
import com.stacklens.application.port.out.knowledge.KnowledgeBundlePort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ApplicationTransactional
import java.io.File

@ApplicationService
class KnowledgeImportService(
    private val fileReadWritePort: FileReadWritePort,
    private val bundlePort: KnowledgeBundlePort,
    internal val persistence: KnowledgeArchivePersistence,
) : KnowledgeImportUseCase,
    KnowledgeLocalFileImportUseCase,
    KnowledgeImporter {
    @ApplicationTransactional
    override fun import(request: KnowledgeImportRequest): KnowledgeImportResult = persistence.import(request)

    @ApplicationTransactional
    override fun importFromLocalFile(
        path: String,
        password: String?,
        mode: KnowledgeImportMode,
    ): List<KnowledgeImportResult> {
        val file = File(path)
        val bytes = readLocalArchive(file, path)
        val effectivePassword =
            password?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Passwort ist erforderlich")
        return when (KnowledgeArchiveFormatDetector.detect(bytes)) {
            KnowledgeArchiveFormatDetector.Kind.BUNDLE -> importBundleArchive(bytes, effectivePassword, mode)
            KnowledgeArchiveFormatDetector.Kind.SINGLE -> listOf(importSingleArchive(bytes, effectivePassword, mode))
            KnowledgeArchiveFormatDetector.Kind.UNKNOWN -> throw unknownArchiveFormat(file)
        }
    }

    @ApplicationTransactional
    override fun importAggregatedFromLocalFile(
        path: String,
        password: String?,
        mode: KnowledgeImportMode,
    ): List<KnowledgeImportResult> {
        val file = File(path)
        val bytes = readLocalArchive(file, path)
        val effectivePassword = password?.takeIf { it.isNotBlank() }
        return when (KnowledgeArchiveFormatDetector.detect(bytes)) {
            KnowledgeArchiveFormatDetector.Kind.BUNDLE -> {
                if (effectivePassword == null) {
                    throw IllegalArgumentException("Passwort ist erforderlich")
                }
                listOf(aggregateBundleResults(importBundleArchive(bytes, effectivePassword, mode), mode))
            }
            KnowledgeArchiveFormatDetector.Kind.SINGLE -> listOf(importSingleArchive(bytes, effectivePassword, mode))
            KnowledgeArchiveFormatDetector.Kind.UNKNOWN -> throw unknownArchiveFormat(file)
        }
    }

    private fun importBundleArchive(
        bundleBytes: ByteArray,
        password: String,
        mode: KnowledgeImportMode,
    ): List<KnowledgeImportResult> {
        val entries = bundlePort.inspect(bundleBytes)
        val results = ArrayList<KnowledgeImportResult>(entries.size)
        for (entry in entries) {
            val archiveBytes = bundlePort.readEntry(bundleBytes, entry.entryName)
            results.add(
                import(
                    KnowledgeImportRequest(
                        archiveBytes = archiveBytes,
                        password = password,
                        mode = mode,
                        targetKnowledgeBaseName = entry.knowledgeBaseName,
                    ),
                ),
            )
        }
        return results
    }

    private fun importSingleArchive(
        bytes: ByteArray,
        password: String?,
        mode: KnowledgeImportMode,
    ): KnowledgeImportResult =
        import(
            KnowledgeImportRequest(
                archiveBytes = bytes,
                password = password,
                mode = mode,
                includeVectors = true,
            ),
        )

    private fun aggregateBundleResults(
        results: List<KnowledgeImportResult>,
        mode: KnowledgeImportMode,
    ): KnowledgeImportResult =
        KnowledgeImportResult(
            knowledgeBaseId = results.firstOrNull()?.knowledgeBaseId ?: 0L,
            knowledgeBaseName =
                results
                    .joinToString(", ") { it.knowledgeBaseName }
                    .ifBlank { "Bundle-Import" },
            documentsImported = results.sumOf { it.documentsImported },
            chunksImported = results.sumOf { it.chunksImported },
            vectorsImported = results.sumOf { it.vectorsImported },
            mode = mode,
        )

    private fun readLocalArchive(
        file: File,
        path: String,
    ): ByteArray {
        if (!fileReadWritePort.exists(file) || !fileReadWritePort.isFile(file)) {
            throw IllegalArgumentException("Datei nicht gefunden: $path")
        }
        val allowed = setOf("slknowledge", "slbundle")
        val ext = file.name.substringAfterLast('.', "").lowercase()
        if (ext !in allowed) {
            throw IllegalArgumentException("Ungültige Dateiendung: ${file.name}. Erwartet: ${allowed.joinToString(", ")}")
        }
        return fileReadWritePort.readBytes(file)
    }

    private fun unknownArchiveFormat(file: File): IllegalArgumentException =
        IllegalArgumentException(
            "Unbekanntes Archivformat: ${file.name}. Erwartet wird eine .slknowledge- oder .slbundle-Datei.",
        )
}
