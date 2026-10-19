package com.stacklens.application.service.knowledge

import com.stacklens.application.collaboration.KnowledgeBaseReader
import com.stacklens.application.collaboration.KnowledgeExporter
import com.stacklens.application.collaboration.KnowledgeImporter
import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportResult
import com.stacklens.application.port.out.knowledge.KnowledgeBundleEntry
import com.stacklens.application.port.out.knowledge.KnowledgeBundlePort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KnowledgeBundleServiceImportCoverageTest {
    private val bundlePort: KnowledgeBundlePort = mock()
    private val baseUseCase: KnowledgeBaseReader = mock()
    private val singleBaseExport: KnowledgeExporter = mock()
    private val singleBaseImport: KnowledgeImporter = mock()
    private val service = KnowledgeBundleService(bundlePort, baseUseCase, singleBaseExport, singleBaseImport)

    private fun entry(
        name: String,
        baseName: String,
    ) = KnowledgeBundleEntry(
        entryName = "bases/$name.slknowledge",
        displayName = baseName,
        knowledgeBaseStableId = "stable-$name",
        knowledgeBaseName = baseName,
        knowledgeBaseDescription = "description of $baseName",
        sourceCount = 1,
        documentCount = 2,
        chunkCount = 3,
        containsVectors = true,
    )

    private fun result(
        id: Long,
        name: String,
    ) = KnowledgeImportResult(
        knowledgeBaseId = id,
        knowledgeBaseName = name,
        documentsImported = 2,
        chunksImported = 3,
        vectorsImported = 0,
        mode = KnowledgeImportMode.MERGE,
    )

    @Test
    fun `importing demands a password`() {
        val failure = assertThrows(IllegalArgumentException::class.java) { service.import(ByteArray(1), "  ", KnowledgeImportMode.SKIP_EXISTING) }

        assertTrue(failure.message!!.contains("Password is required"))
    }

    @Test
    fun `every bundle entry is imported into its own base`() {
        whenever(bundlePort.inspect(any())).thenReturn(listOf(entry("team", "Team"), entry("ops", "Ops")))
        whenever(bundlePort.readEntry(any(), eq("bases/team.slknowledge"))).thenReturn(byteArrayOf(1))
        whenever(bundlePort.readEntry(any(), eq("bases/ops.slknowledge"))).thenReturn(byteArrayOf(2))
        whenever(singleBaseImport.import(any())).thenReturn(result(11L, "Team"), result(12L, "Ops"))

        val results = service.import(byteArrayOf(9), "pw", KnowledgeImportMode.SKIP_EXISTING)

        assertEquals(listOf(11L, 12L), results.map { it.knowledgeBaseId })
    }

    @Test
    fun `a bundle without entries imports nothing`() {
        whenever(bundlePort.inspect(any())).thenReturn(emptyList())

        assertEquals(0, service.import(byteArrayOf(9), "pw", KnowledgeImportMode.REPLACE).size)
    }

    @Test
    fun `a single base export keeps its own description`() {
        whenever(baseUseCase.listBases()).thenReturn(
            listOf(
                com.stacklens.domain.model.knowledge
                    .KnowledgeBase(id = 1L, name = "Team"),
            ),
        )
        whenever(singleBaseExport.exportBase(any(), any(), any())).thenReturn(byteArrayOf(3))
        whenever(singleBaseExport.inspect(any(), any())).thenReturn(
            com.stacklens.application.port.model.KnowledgeImportPreview(
                knowledgeBaseName = "Archiv",
                sourceCount = 1,
                documentCount = 1,
                chunkCount = 1,
                containsVectors = false,
                embeddingProvider = null,
                embeddingModel = null,
                embeddingDimension = null,
            ),
        )
        whenever(bundlePort.write(any())).thenAnswer { inv ->
            "bundle".toByteArray()
        }

        val bytes =
            service.exportAll(
                com.stacklens.domain.model.knowledge
                    .KnowledgeExportOptions(),
                "pw",
            )

        assertTrue(bytes.isNotEmpty())
    }
}
