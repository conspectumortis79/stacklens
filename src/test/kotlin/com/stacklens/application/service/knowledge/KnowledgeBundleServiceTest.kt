package com.stacklens.application.service.knowledge

import com.stacklens.application.collaboration.KnowledgeBaseReader
import com.stacklens.application.collaboration.KnowledgeExporter
import com.stacklens.application.collaboration.KnowledgeImporter
import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportPreview
import com.stacklens.application.port.model.KnowledgeImportResult
import com.stacklens.application.port.out.knowledge.KnowledgeBundleEntry
import com.stacklens.application.port.out.knowledge.KnowledgeBundlePort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeBundleServiceTest {
    private val bundlePort: KnowledgeBundlePort = mock()
    private val baseUseCase: KnowledgeBaseReader = mock()
    private val singleBaseExport: KnowledgeExporter = mock()
    private val singleBaseImport: KnowledgeImporter = mock()
    private val service = KnowledgeBundleService(bundlePort, baseUseCase, singleBaseExport, singleBaseImport)

    private val options = KnowledgeExportOptions()

    private fun base(
        id: Long,
        name: String,
        stableId: String = "stable-$id",
    ): KnowledgeBase = KnowledgeBase(id = id, stableId = stableId, name = name, description = "Beschreibung $name")

    private fun stubSingleBaseExport(
        name: String,
        bytes: ByteArray = "archive".toByteArray(),
    ) {
        whenever(singleBaseExport.exportBase(any(), any(), any())).thenReturn(bytes)
        whenever(singleBaseExport.inspect(eq(bytes), any())).thenReturn(
            KnowledgeImportPreview(
                knowledgeBaseName = name,
                sourceCount = 1,
                documentCount = 2,
                chunkCount = 3,
                containsVectors = true,
                embeddingProvider = null,
                embeddingModel = null,
                embeddingDimension = null,
            ),
        )
    }

    @Test fun `export requires a password`() {
        val failure =
            assertThrows(IllegalArgumentException::class.java) { service.exportAll(options, "   ") }

        assertTrue(failure.message!!.contains("password must not be blank"))
    }

    @Test fun `export refuses a workspace without knowledge bases`() {
        whenever(baseUseCase.listBases()).thenReturn(emptyList())

        val failure =
            assertThrows(IllegalStateException::class.java) { service.exportAll(options, "pw") }

        assertTrue(failure.message!!.contains("Keine Wissensdatenbanken"))
    }

    @Test fun `export names each archive after its base and keeps them unique`() {
        whenever(baseUseCase.listBases())
            .thenReturn(
                listOf(
                    base(1L, "Team Wissen"),
                    base(2L, "Team Wissen"),
                    base(3L, "??"),
                ),
            )
        stubSingleBaseExport("  ")
        val written = mutableListOf<com.stacklens.application.port.out.knowledge.KnowledgeBundleArchive>()
        whenever(bundlePort.write(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            written += invocation.arguments[0] as List<com.stacklens.application.port.out.knowledge.KnowledgeBundleArchive>
            "bundle".toByteArray()
        }

        val bytes = service.exportAll(options, "pw")

        assertEquals("bundle".toByteArray().toList(), bytes.toList())
        assertEquals(
            listOf("bases/team-wissen.slknowledge", "bases/team-wissen-2.slknowledge", "bases/base.slknowledge"),
            written.map { it.entryName },
        )
        assertEquals(listOf("Team Wissen", "Team Wissen", "??"), written.map { it.displayName })
        assertEquals(listOf("stable-1", "stable-2", "stable-3"), written.map { it.knowledgeBaseStableId })
        assertEquals(1, written.first().sourceCount)
        assertEquals(2, written.first().documentCount)
        assertEquals(3, written.first().chunkCount)
        assertEquals("Beschreibung Team Wissen", written.first().knowledgeBaseDescription)
    }

    @Test fun `export falls back to the stored name when the archive preview is blank`() {
        whenever(baseUseCase.listBases()).thenReturn(listOf(base(7L, "Gespeicherte Basis")))
        stubSingleBaseExport("  ")
        val written = mutableListOf<com.stacklens.application.port.out.knowledge.KnowledgeBundleArchive>()
        whenever(bundlePort.write(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            written += invocation.arguments[0] as List<com.stacklens.application.port.out.knowledge.KnowledgeBundleArchive>
            "bundle".toByteArray()
        }

        service.exportAll(options, "pw")

        assertEquals("Gespeicherte Basis", written.single().knowledgeBaseName)
        assertEquals("bases/gespeicherte-basis.slknowledge", written.single().entryName)
    }

    @Test fun `inspect mirrors the bundle entries into a preview`() {
        whenever(bundlePort.inspect(any())).thenReturn(
            listOf(
                KnowledgeBundleEntry(
                    entryName = "bases/a.slknowledge",
                    displayName = "A",
                    knowledgeBaseStableId = "stable-a",
                    knowledgeBaseName = "A",
                    knowledgeBaseDescription = "desc a",
                    sourceCount = 1,
                    documentCount = 2,
                    chunkCount = 3,
                    containsVectors = false,
                ),
            ),
        )

        val preview = service.inspect("bytes".toByteArray())

        assertEquals(1, preview.entryCount)
        assertEquals("bases/a.slknowledge", preview.bases.single().entryName)
        assertEquals("stable-a", preview.bases.single().knowledgeBaseStableId)
        assertEquals(false, preview.bases.single().containsVectors)
    }

    @Test fun `import requires a password`() {
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                service.import("bytes".toByteArray(), "", KnowledgeImportMode.NEW_COPY)
            }

        assertTrue(failure.message!!.contains("Password is required"))
    }

    @Test fun `import decrypts and restores every entry of the bundle`() {
        val bundle = "bundle".toByteArray()
        whenever(bundlePort.inspect(bundle)).thenReturn(
            listOf(
                KnowledgeBundleEntry(
                    entryName = "bases/a.slknowledge",
                    displayName = "A",
                    knowledgeBaseStableId = "stable-a",
                    knowledgeBaseName = "A",
                    knowledgeBaseDescription = "",
                    sourceCount = 0,
                    documentCount = 0,
                    chunkCount = 0,
                    containsVectors = false,
                ),
                KnowledgeBundleEntry(
                    entryName = "bases/b.slknowledge",
                    displayName = "B",
                    knowledgeBaseStableId = "stable-b",
                    knowledgeBaseName = "B",
                    knowledgeBaseDescription = "",
                    sourceCount = 0,
                    documentCount = 0,
                    chunkCount = 0,
                    containsVectors = false,
                ),
            ),
        )
        whenever(bundlePort.readEntry(eq(bundle), any())).thenReturn("inner".toByteArray())
        whenever(singleBaseImport.import(any())).thenReturn(
            KnowledgeImportResult(1L, "A", 2, 3, 0, KnowledgeImportMode.NEW_COPY),
            KnowledgeImportResult(2L, "B", 4, 5, 0, KnowledgeImportMode.NEW_COPY),
        )

        val results = service.import(bundle, "pw", KnowledgeImportMode.NEW_COPY)

        assertEquals(listOf(1L, 2L), results.map { it.knowledgeBaseId })
        verify(singleBaseImport, org.mockito.kotlin.times(2)).import(
            argThat { request -> request.archiveBytes.contentEquals("inner".toByteArray()) && request.password == "pw" },
        )
    }
}
